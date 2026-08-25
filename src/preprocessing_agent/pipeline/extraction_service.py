"""RAG-008 extraction lifecycle and the single-column walking skeleton."""
from __future__ import annotations

import hashlib
import json
import uuid
import shutil
import tempfile
import fcntl
import re
import os
import math
import contextlib
import sys
import os
from dataclasses import dataclass, field
from enum import Enum
from pathlib import Path
from typing import Any, Mapping, Protocol

from preprocessing_agent.domain import PageStatus, ParsedDocument, ParsedPage, ParsedBlock, SourceSpan
from preprocessing_agent.domain.layout import BoundingBox, PageGeometry, LayoutBlock
from preprocessing_agent.parsers.pdf import PdfDocumentParser
from preprocessing_agent.pipeline.pipeline import PreprocessingPipeline


class ExtractionStatus(str, Enum):
    QUEUED = "QUEUED"
    PROCESSING = "PROCESSING"
    VALIDATING = "VALIDATING"
    READY = "READY"
    NEEDS_REVIEW = "NEEDS_REVIEW"


class VersionNotFoundError(ValueError):
    pass


@dataclass
class PageExtraction:
    page_number: int
    status: PageStatus = PageStatus.PENDING
    findings: list[str] = field(default_factory=list)

    @classmethod
    def validated(cls, page_number: int) -> "PageExtraction":
        return cls(page_number, PageStatus.VALIDATED)

    @classmethod
    def needs_review(cls, page_number: int, finding: str) -> "PageExtraction":
        return cls(page_number, PageStatus.NEEDS_REVIEW, [finding])


@dataclass
class ExtractionVersion:
    version_id: str
    document_id: str
    policy_version: str
    page_count: int
    status: ExtractionStatus = ExtractionStatus.QUEUED
    pages: dict[int, PageExtraction] = field(default_factory=dict)

    @classmethod
    def create(cls, version_id: str, document_id: str, policy_version: str, page_count: int) -> "ExtractionVersion":
        if not version_id or not document_id or not policy_version or page_count < 1:
            raise ValueError("version identity and positive page count are required")
        return cls(version_id, document_id, policy_version, page_count, ExtractionStatus.PROCESSING)

    def record_page(self, page: PageExtraction) -> None:
        if self.status == ExtractionStatus.READY:
            raise ValueError("published extraction version is immutable")
        if not 1 <= page.page_number <= self.page_count:
            raise ValueError("page number is outside extraction version")
        self.pages[page.page_number] = page
        self.status = ExtractionStatus.NEEDS_REVIEW if any(item.status == PageStatus.NEEDS_REVIEW for item in self.pages.values()) else ExtractionStatus.VALIDATING
        if page.status == PageStatus.NEEDS_REVIEW:
            self.status = ExtractionStatus.NEEDS_REVIEW

    def publish(self) -> None:
        if len(self.pages) != self.page_count or any(page.status != PageStatus.VALIDATED for page in self.pages.values()):
            self.status = ExtractionStatus.NEEDS_REVIEW
            raise ValueError("cannot publish extraction version with unvalidated pages")
        self.status = ExtractionStatus.READY


class NativePdfPort(Protocol):
    def extract(self, source: Path) -> list[Mapping[str, Any]]: ...


class PyMuPdfNativePdfAdapter:
    def extract(self, source: Path) -> list[Mapping[str, Any]]:
        try:
            with contextlib.redirect_stdout(sys.stderr):
                import fitz  # type: ignore
        except ImportError as exc:
            raise RuntimeError("PyMuPDF is required for PDF process requests") from exc
        pages: list[Mapping[str, Any]] = []
        with fitz.open(source) as document:
            for number, page in enumerate(document, 1):
                rect = page.rect
                blocks = []
                for index, raw in enumerate(page.get_text("blocks")):
                    text = str(raw[4]).strip()
                    if not text:
                        continue
                    blocks.append({"block_id": f"p{number}-b{index}", "text": text, "bbox": raw[:4]})
                pages.append({"page_number": number, "geometry": {"width": rect.width, "height": rect.height}, "blocks": blocks})
        return pages


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _canonical_path(value: str, *, require_file: bool = False) -> Path:
    raw = Path(value).expanduser()
    if not raw.is_absolute() or str(raw).startswith("/mnt/"):
        raise ValueError("INVALID_REQUEST")
    if any(part in {".", ".."} for part in raw.parts):
        raise ValueError("INVALID_REQUEST")
    resolved = raw.resolve()
    if require_file and not resolved.is_file():
        raise ValueError("SOURCE_NOT_FOUND")
    cursor = raw
    for parent in [cursor, *cursor.parents]:
        if parent.is_symlink():
            raise ValueError("INVALID_REQUEST")
    return resolved


def _enforce_root(path: Path, env_name: str) -> None:
    configured = os.environ.get(env_name, "/tmp")
    root = _canonical_path(configured)
    try:
        path.relative_to(root)
    except ValueError as exc:
        raise ValueError("INVALID_REQUEST") from exc


class ExtractionApplicationService:
    def __init__(self, native_pdf: NativePdfPort | None = None) -> None:
        self.native_pdf = native_pdf or PyMuPdfNativePdfAdapter()
        self._versions: dict[str, Mapping[str, Any]] = {}

    def preprocess(self, request: Mapping[str, Any]) -> Mapping[str, Any]:
        output = _canonical_path(str(request.get("output_dir", "")))
        _enforce_root(output, "PREPROCESS_OUTPUT_ROOT")
        output.mkdir(parents=True, exist_ok=True)
        with (output / ".preprocess.lock").open("a+") as lock:
            fcntl.flock(lock.fileno(), fcntl.LOCK_EX)
            try:
                return self._preprocess_locked(request, output)
            finally:
                fcntl.flock(lock.fileno(), fcntl.LOCK_UN)

    def _preprocess_locked(self, request: Mapping[str, Any], canonical_output: Path) -> Mapping[str, Any]:
        source = _canonical_path(str(request["source_path"]), require_file=True)
        _enforce_root(source, "PREPROCESS_INPUT_ROOT")
        output = canonical_output
        request_id = str(request["request_id"])
        policy = str(request["policy_version"])
        source_hash = _sha256(source)
        if request.get("source_sha256") is not None and request["source_sha256"] != source_hash:
            raise ValueError("SOURCE_HASH_MISMATCH")
        output.mkdir(parents=True, exist_ok=True)
        index_path = output / "request-index.json"
        try:
            index = json.loads(index_path.read_text()) if index_path.exists() else {}
            if not isinstance(index, dict):
                raise ValueError("not an object")
        except (json.JSONDecodeError, ValueError):
            if index_path.exists():
                index_path.replace(index_path.with_name("request-index.corrupt.json"))
            index = {}
        requested_version = request.get("version_id")
        idempotency_key = f"{request_id}:{source_hash}:{policy}:{requested_version or ''}"
        base_key = f"{request_id}:{source_hash}:{policy}:"
        if requested_version:
            for key, existing in index.items():
                if key.startswith(base_key) and not key.endswith(f":{requested_version}"):
                    raise ValueError("VERSION_ID_CONFLICT")
        if idempotency_key in index:
            saved = output / "versions" / index[idempotency_key] / "response.json"
            if saved.exists() and not saved.is_symlink():
                try:
                    cached = json.loads(saved.read_text())
                    if cached.get("version_id") == index[idempotency_key]:
                        # Replay uses the same complete read-model validation as status;
                        # a partially written/tampered cache is never a fast return.
                        validated = self.get_status(index[idempotency_key], output, _lock=False)
                        if validated.get("version_id") == cached.get("version_id") and validated.get("status") == cached.get("status"):
                            return validated
                except (OSError, ValueError, TypeError, json.JSONDecodeError):
                    pass
                index.pop(idempotency_key, None)
        try:
            raw_pages = self.native_pdf.extract(source) if source.suffix.lower() == ".pdf" else self._text_pages(source)
            if not isinstance(raw_pages, (list, tuple)):
                raw_pages = [{"page_number": 1, "malformed": True}]
        except Exception as exc:
            raise ValueError("NATIVE_EXTRACTION_FAILED") from exc
        document_id = source_hash[:16]
        version_id = str(request.get("version_id") or f"ev-{uuid.uuid4().hex[:12]}")
        if not re.fullmatch(r"[A-Za-z0-9._-]+", version_id) or version_id in {".", ".."}:
            raise ValueError("INVALID_REQUEST")
        version = ExtractionVersion.create(version_id, document_id, policy, len(raw_pages) or 1)
        parser_pages: list[Mapping[str, Any]] = []
        page_artifacts: list[dict[str, Any]] = []
        seen_page_numbers: set[int] = set()
        for position, raw in enumerate(raw_pages, 1):
            number: int | None = None
            try:
                if not isinstance(raw, Mapping):
                    raise ValueError("MALFORMED_EXTRACTION_PAYLOAD")
                geometry_raw = raw.get("geometry", {})
                raw_number = raw.get("page_number", position)
                if type(raw_number) is not int:
                    raise ValueError("INVALID_PAGE_METADATA")
                number = raw_number
                if number < 1 or number > len(raw_pages):
                    raise ValueError("INVALID_PAGE_METADATA")
                if number in seen_page_numbers:
                    raise ValueError("DUPLICATE_PAGE_NUMBER")
                seen_page_numbers.add(number)
                geometry = PageGeometry(float(geometry_raw["width"]), float(geometry_raw["height"]))
                if not math.isfinite(geometry.width) or not math.isfinite(geometry.height):
                    raise ValueError("INVALID_GEOMETRY")
                blocks = []
                for block in raw.get("blocks", ()):
                    bbox = BoundingBox(*(float(item) for item in block["bbox"]))
                    if not all(math.isfinite(value) for value in (bbox.x0, bbox.y0, bbox.x1, bbox.y1)):
                        raise ValueError("INVALID_GEOMETRY")
                    if not geometry.contains(bbox):
                        raise ValueError("INVALID_GEOMETRY")
                    LayoutBlock(str(block.get("block_id", "")), str(block.get("kind", "text")), bbox, str(block.get("text", "")), str(block.get("extraction_method", "native")), float(block.get("confidence", 1.0)), document_id, number, geometry)
                    blocks.append({**block, "bbox": [bbox.x0, bbox.y0, bbox.x1, bbox.y1], "source_document_id": document_id, "page_number": number, "page_geometry": {"width": geometry.width, "height": geometry.height, "unit": geometry.unit, "origin": geometry.origin}})
                raw = {**raw, "blocks": blocks}
                version.record_page(PageExtraction.validated(number))
                parser_pages.append(raw)
                evidence = {"page_number": number, "geometry": {"width": geometry.width, "height": geometry.height, "unit": geometry.unit, "origin": geometry.origin}, "blocks": blocks}
                page_artifacts.append({**evidence, "status": PageStatus.VALIDATED.value, "evidence_sha256": hashlib.sha256(json.dumps(evidence, sort_keys=True).encode()).hexdigest()})
            except (KeyError, TypeError, ValueError) as exc:
                safe_number = number if "number" in locals() and 1 <= number <= version.page_count else position
                version.record_page(PageExtraction.needs_review(safe_number, str(exc) or "INVALID_GEOMETRY"))
                page_artifacts[:] = [item for item in page_artifacts if item.get("page_number") != safe_number]
                evidence = {"page_number": safe_number, "status": PageStatus.NEEDS_REVIEW.value, "findings": version.pages[safe_number].findings}
                page_artifacts.append({**evidence, "evidence_sha256": hashlib.sha256(json.dumps(evidence, sort_keys=True).encode()).hexdigest()})
        present_pages = {item["page_number"] for item in page_artifacts}
        for missing in range(1, version.page_count + 1):
            if missing not in present_pages:
                evidence = {"page_number": missing, "status": PageStatus.NEEDS_REVIEW.value, "findings": ["MISSING_PAGE_METADATA"]}
                page_artifacts.append({**evidence, "evidence_sha256": hashlib.sha256(json.dumps(evidence, sort_keys=True).encode()).hexdigest()})
        ready = False
        manifest: Mapping[str, Any] = {}
        output.mkdir(parents=True, exist_ok=True)
        versions = output / "versions"
        _canonical_path(str(versions))
        versions.mkdir(exist_ok=True)
        temp_dir = Path(tempfile.mkdtemp(prefix=f".{version_id}-", dir=str(versions)))
        try:
            if len(version.pages) == version.page_count and all(page.status == PageStatus.VALIDATED for page in version.pages.values()) and version.pages:
                version.publish()
                parser = PdfDocumentParser(extractor=lambda _source: parser_pages)
                result = PreprocessingPipeline.from_config({"name": "extraction-version", "pipeline_version": policy}, parser=parser, output_dir=temp_dir).run(source=source)
                manifest = dict(result.manifest)
                manifest_path = temp_dir / "manifest.json"
                manifest_data = json.loads(manifest_path.read_text())
                manifest_data["source_sha256"] = source_hash
                manifest_data.setdefault("source", {})["sha256"] = source_hash
                manifest_path.write_text(json.dumps(manifest_data, ensure_ascii=False, sort_keys=True, indent=2) + "\n")
                manifest = manifest_data
                ready = True
            else:
                version.status = ExtractionStatus.NEEDS_REVIEW
                (temp_dir / "manifest.json").write_text(json.dumps({"source": {"path": str(source), "sha256": source_hash}, "source_sha256": source_hash, "pipeline_version": policy, "schema_version": "1", "profile": "extraction-version", "policy": {"version": policy}, "statistics": {"pages": version.page_count}, "page_count": version.page_count, "status": version.status.value, "version_id": version.version_id, "document_id": document_id, "policy_version": policy}, sort_keys=True) + "\n")
            version_artifact = {"version_id": version.version_id, "document_id": document_id, "policy_version": policy, "page_count": version.page_count, "status": version.status.value, "source_sha256": source_hash, "pages": page_artifacts}
            (temp_dir / "version.json").write_text(json.dumps(version_artifact, ensure_ascii=False, sort_keys=True, indent=2) + "\n")
            response = {"schema_version": "1", "operation": "preprocess", "request_id": request_id, "version_id": version.version_id, "status": version.status.value, "pages": [{"page_number": item["page_number"], "status": item["status"], "attempts": 1, "findings": item.get("findings", [])} for item in page_artifacts], "page_summary": {"count": len(page_artifacts), "processed": len(page_artifacts), "validated": sum(item["status"] == "VALIDATED" for item in page_artifacts), "needs_review": sum(item["status"] == "NEEDS_REVIEW" for item in page_artifacts), "ready": sum(item["status"] == "VALIDATED" for item in page_artifacts)}, "artifacts": self._artifact_refs(temp_dir, ready), "manifest": manifest}
            (temp_dir / "response.json").write_text(json.dumps(response, ensure_ascii=False, sort_keys=True, indent=2) + "\n")
            version_dir = versions / version_id
            if version_dir.exists():
                raise ValueError("VERSION_ID_CONFLICT")
            temp_dir.rename(version_dir)
            if ready:
                try:
                    generations = output / "generations"
                    _canonical_path(str(generations))
                    generations.mkdir(exist_ok=True)
                    generation_stage = generations / f".{version_id}.tmp"
                    if generation_stage.exists(): shutil.rmtree(generation_stage)
                    shutil.copytree(version_dir, generation_stage)
                    generation = generations / version_id
                    if generation.exists(): shutil.rmtree(generation)
                    generation_stage.rename(generation)
                except Exception:
                    raise
                finally:
                    pass
                (output / "current.json.tmp").write_text(json.dumps({"version_id": version_id, "generation": str(generation), "status": version.status.value}, sort_keys=True) + "\n")
                (output / "current.json.tmp").replace(output / "current.json")
            else:
                # Keep the prior current generation visible; this attempt is version-scoped.
                pass
            response = {**response, "artifacts": self._artifact_refs(version_dir, ready)}
            if ready and 'generation' in locals():
                response = {**response, "artifacts": self._artifact_refs(generation, ready)}
            (version_dir / "response.json").write_text(json.dumps(response, ensure_ascii=False, sort_keys=True, indent=2) + "\n")
            if ready and 'generation' in locals():
                shutil.copy2(version_dir / "response.json", generation / "response.json")
            index[idempotency_key] = version_id
            index_tmp = index_path.with_suffix(".tmp")
            index_tmp.write_text(json.dumps(index, sort_keys=True, indent=2) + "\n")
            os.replace(index_tmp, index_path)
            return response
        except Exception as exc:
            if temp_dir.exists(): shutil.rmtree(temp_dir)
            raise

    def get_status(self, version_id: str, artifact_root: str | Path, *, _lock: bool = True) -> Mapping[str, Any]:
        if not re.fullmatch(r"[A-Za-z0-9._-]+", version_id) or version_id in {".", ".."}:
            raise ValueError("INVALID_REQUEST")
        root = _canonical_path(str(artifact_root))
        _enforce_root(root, "PREPROCESS_ARTIFACT_ROOT")
        status_lock = (root / ".preprocess.lock").open("a+") if _lock else None
        if status_lock is not None:
            fcntl.flock(status_lock.fileno(), fcntl.LOCK_SH)
        try:
            current = root / "current.json"
            selected = root / "generations" / version_id / "response.json"
            if not current.exists() or json.loads(current.read_text()).get("version_id") != version_id:
                selected = root / "versions" / version_id / "response.json"
            path = selected
            if path.is_symlink() or any(parent.is_symlink() for parent in (path, *path.parents)):
                raise ValueError("VERSION_ARTIFACT_CORRUPT")
            resolved_path = path.resolve()
            allowed_response_roots = (root / "generations" / version_id, root / "versions" / version_id)
            if not any(resolved_path.is_relative_to(response_root.resolve()) for response_root in allowed_response_roots):
                raise ValueError("VERSION_ARTIFACT_CORRUPT")
            path = resolved_path
            if True:
                if not path.exists():
                    raise VersionNotFoundError("VERSION_NOT_FOUND")
                try:
                    response = json.loads(path.read_text())
                except json.JSONDecodeError as exc:
                    raise ValueError("VERSION_ARTIFACT_CORRUPT") from exc
                if not isinstance(response, dict) or not all(key in response for key in ("schema_version", "operation", "request_id", "version_id", "status", "pages", "page_summary", "artifacts", "manifest")) or not isinstance(response["pages"], list) or not isinstance(response["artifacts"], dict) or not isinstance(response["page_summary"], dict) or not isinstance(response["manifest"], dict):
                    raise ValueError("VERSION_ARTIFACT_CORRUPT")
                if response["schema_version"] != "1" or response["operation"] not in {"preprocess", "status"} or not isinstance(response["request_id"], str) or not response["request_id"] or response["version_id"] != version_id or response["status"] not in {"QUEUED", "PROCESSING", "VALIDATING", "READY", "NEEDS_REVIEW"}:
                    raise ValueError("VERSION_ARTIFACT_CORRUPT")
                summary = response.get("page_summary", {})
                if not isinstance(summary, dict) or any(type(summary.get(key)) is not int for key in ("count", "processed", "validated", "needs_review", "ready")) or summary.get("count") != len(response["pages"]) or summary.get("processed") != len(response["pages"]):
                    raise ValueError("VERSION_ARTIFACT_CORRUPT")
                statuses = [page.get("status") for page in response["pages"] if isinstance(page, dict)]
                if summary.get("validated") != statuses.count("VALIDATED") or summary.get("needs_review") != statuses.count("NEEDS_REVIEW") or summary.get("ready") != statuses.count("VALIDATED"):
                    raise ValueError("VERSION_ARTIFACT_CORRUPT")
                if response["status"] == "READY" and any(page.get("status") == "NEEDS_REVIEW" for page in response["pages"]):
                    raise ValueError("VERSION_ARTIFACT_CORRUPT")
                page_numbers = [page.get("page_number") if isinstance(page, dict) else None for page in response["pages"]]
                if len(set(page_numbers)) != len(page_numbers) or any(type(number) is not int or number < 1 for number in page_numbers):
                    raise ValueError("VERSION_ARTIFACT_CORRUPT")
                for page in response["pages"]:
                    if not isinstance(page, dict) or type(page.get("page_number")) is not int or page.get("status") not in {"VALIDATED", "NEEDS_REVIEW"} or type(page.get("attempts")) is not int or page.get("attempts") < 1 or not isinstance(page.get("findings"), list) or any(not isinstance(item, str) for item in page.get("findings", [])):
                        raise ValueError("VERSION_ARTIFACT_CORRUPT")
                if not isinstance(response["artifacts"].get("manifest_sha256"), (str, type(None))):
                    raise ValueError("VERSION_ARTIFACT_CORRUPT")
                manifest_ref = response["artifacts"].get("manifest")
                if not isinstance(manifest_ref, dict) or not re.fullmatch(r"[0-9a-f]{64}", str(response["artifacts"].get("manifest_sha256"))) or response["artifacts"].get("manifest_sha256") != manifest_ref.get("sha256"):
                    raise ValueError("VERSION_ARTIFACT_CORRUPT")
                allowed_refs = {"manifest_sha256", "manifest", "version", "chunks", "document_tree", "issues"}
                if set(response["artifacts"]) - allowed_refs or not {"manifest_sha256", "manifest", "version"} <= set(response["artifacts"]):
                    raise ValueError("VERSION_ARTIFACT_CORRUPT")
                for ref in response["artifacts"].values():
                    if isinstance(ref, dict) and "path" in ref and "sha256" in ref:
                        if set(ref) != {"path", "sha256"}:
                            raise ValueError("VERSION_ARTIFACT_CORRUPT")
                        if not isinstance(ref["path"], str) or not isinstance(ref["sha256"], str):
                            raise ValueError("VERSION_ARTIFACT_CORRUPT")
                        raw_target = Path(ref["path"])
                        if raw_target.is_symlink() or any(parent.is_symlink() for parent in (raw_target, *raw_target.parents)):
                            raise ValueError("VERSION_ARTIFACT_CORRUPT")
                        target = raw_target.resolve()
                        generation_root = root / "generations" / version_id
                        version_root = root / "versions" / version_id
                        if not (target.parent == version_root or target.parent == generation_root) or not re.fullmatch(r"[0-9a-f]{64}", str(ref["sha256"])) or not target.exists() or _sha256(target) != ref["sha256"]:
                            raise ValueError("VERSION_ARTIFACT_CORRUPT")
                    elif ref is not None and not isinstance(ref, dict) and ref is not response["artifacts"].get("manifest_sha256"):
                        raise ValueError("VERSION_ARTIFACT_CORRUPT")
                manifest_ref = response["artifacts"].get("manifest", {})
                version_ref = response["artifacts"].get("version", {})
                if manifest_ref and version_ref:
                    manifest_data = json.loads(Path(manifest_ref["path"]).read_text())
                    version_data = json.loads(Path(version_ref["path"]).read_text())
                    if manifest_data.get("source_sha256") != version_data.get("source_sha256") or version_data.get("version_id") != version_id or version_data.get("status") != response.get("status"):
                        raise ValueError("VERSION_ARTIFACT_CORRUPT")
                    expected_pages = version_data.get("page_count")
                    if type(expected_pages) is not int or expected_pages != len(response["pages"]) or response["page_summary"].get("count") != expected_pages:
                        raise ValueError("VERSION_ARTIFACT_CORRUPT")
                return {**response, "operation": "status"}
        except VersionNotFoundError:
            raise
        except (ValueError, TypeError, OSError, json.JSONDecodeError) as exc:
            raise ValueError("VERSION_ARTIFACT_CORRUPT") from exc
        finally:
            if status_lock is not None:
                fcntl.flock(status_lock.fileno(), fcntl.LOCK_UN)
                status_lock.close()

    @staticmethod
    def _artifact_refs(directory: Path, ready: bool) -> dict[str, Any]:
        names = ["manifest.json", "version.json"] + (["chunks.jsonl", "document_tree.json", "issues.jsonl"] if ready else [])
        refs: dict[str, Any] = {}
        for name in names:
            path = directory / name
            if path.exists():
                key = name.replace(".jsonl", "").replace(".json", "")
                refs[key] = {"path": str(path), "sha256": _sha256(path)}
        refs["manifest_sha256"] = refs.get("manifest", {}).get("sha256")
        return refs

    @staticmethod
    def _text_pages(source: Path) -> list[Mapping[str, Any]]:
        text = source.read_text(encoding="utf-8")
        blocks = [{"block_id": f"p1-b{i}", "text": line.strip(), "bbox": (0, i * 12, 500, i * 12 + 10)} for i, line in enumerate(text.splitlines()) if line.strip()]
        return [{"page_number": 1, "geometry": {"width": 612, "height": max(792, len(blocks) * 12 + 20)}, "blocks": blocks}]
