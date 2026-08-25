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
        if not 1 <= page.page_number <= self.page_count:
            raise ValueError("page number is outside extraction version")
        self.pages[page.page_number] = page
        self.status = ExtractionStatus.VALIDATING
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
    resolved = raw.resolve()
    if require_file and not resolved.is_file():
        raise ValueError("SOURCE_NOT_FOUND")
    if any(part == "." for part in raw.parts):
        raise ValueError("INVALID_REQUEST")
    return resolved


class ExtractionApplicationService:
    def __init__(self, native_pdf: NativePdfPort | None = None) -> None:
        self.native_pdf = native_pdf or PyMuPdfNativePdfAdapter()
        self._versions: dict[str, Mapping[str, Any]] = {}

    def preprocess(self, request: Mapping[str, Any]) -> Mapping[str, Any]:
        output = _canonical_path(str(request.get("output_dir", "")))
        output.mkdir(parents=True, exist_ok=True)
        with (output / ".preprocess.lock").open("a+") as lock:
            fcntl.flock(lock.fileno(), fcntl.LOCK_EX)
            try:
                return self._preprocess_locked(request, output)
            finally:
                fcntl.flock(lock.fileno(), fcntl.LOCK_UN)

    def _preprocess_locked(self, request: Mapping[str, Any], canonical_output: Path) -> Mapping[str, Any]:
        source = _canonical_path(str(request["source_path"]), require_file=True)
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
        idempotency_key = f"{request_id}:{source_hash}:{policy}"
        if idempotency_key in index:
            saved = output / "versions" / index[idempotency_key] / "response.json"
            if saved.exists():
                return json.loads(saved.read_text())
        try:
            raw_pages = self.native_pdf.extract(source) if source.suffix.lower() == ".pdf" else self._text_pages(source)
            if not isinstance(raw_pages, (list, tuple)):
                raw_pages = [{"page_number": 1, "malformed": True}]
        except Exception as exc:
            raise ValueError("NATIVE_EXTRACTION_FAILED") from exc
        document_id = source_hash[:16]
        version_id = str(request.get("version_id") or f"ev-{uuid.uuid4().hex[:12]}")
        if not re.fullmatch(r"[A-Za-z0-9._-]+", version_id):
            raise ValueError("INVALID_REQUEST")
        version = ExtractionVersion.create(version_id, document_id, policy, len(raw_pages) or 1)
        parser_pages: list[Mapping[str, Any]] = []
        page_artifacts: list[dict[str, Any]] = []
        for position, raw in enumerate(raw_pages, 1):
            number: int | None = None
            try:
                if not isinstance(raw, Mapping):
                    raise ValueError("MALFORMED_EXTRACTION_PAYLOAD")
                geometry_raw = raw.get("geometry", {})
                number = int(raw.get("page_number", position))
                if number < 1 or number > len(raw_pages):
                    raise ValueError("INVALID_PAGE_METADATA")
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
                    LayoutBlock(str(block.get("block_id", "")), str(block.get("kind", "text")), bbox, str(block.get("text", "")), str(block.get("extraction_method", "native")), float(block.get("confidence", 1.0)))
                    blocks.append({**block, "bbox": [bbox.x0, bbox.y0, bbox.x1, bbox.y1]})
                raw = {**raw, "blocks": blocks}
                version.record_page(PageExtraction.validated(number))
                parser_pages.append(raw)
                evidence = {"page_number": number, "geometry": {"width": geometry.width, "height": geometry.height, "unit": geometry.unit, "origin": geometry.origin}, "blocks": blocks}
                page_artifacts.append({**evidence, "status": PageStatus.VALIDATED.value, "evidence_sha256": hashlib.sha256(json.dumps(evidence, sort_keys=True).encode()).hexdigest()})
            except (KeyError, TypeError, ValueError) as exc:
                safe_number = number if "number" in locals() and 1 <= number <= version.page_count else position
                version.record_page(PageExtraction.needs_review(safe_number, str(exc) or "INVALID_GEOMETRY"))
                evidence = {"page_number": safe_number, "status": PageStatus.NEEDS_REVIEW.value, "findings": version.pages[safe_number].findings}
                page_artifacts.append({**evidence, "evidence_sha256": hashlib.sha256(json.dumps(evidence, sort_keys=True).encode()).hexdigest()})
        ready = False
        manifest: Mapping[str, Any] = {}
        output.mkdir(parents=True, exist_ok=True)
        versions = output / "versions"
        versions.mkdir(exist_ok=True)
        temp_dir = Path(tempfile.mkdtemp(prefix=f".{version_id}-", dir=str(versions)))
        try:
            if all(page.status == PageStatus.VALIDATED for page in version.pages.values()) and version.pages:
                version.publish()
                parser = PdfDocumentParser(extractor=lambda _source: parser_pages)
                result = PreprocessingPipeline.from_config({"name": "extraction-version", "pipeline_version": policy}, parser=parser, output_dir=temp_dir).run(source=source)
                manifest = dict(result.manifest)
                ready = True
            else:
                version.status = ExtractionStatus.NEEDS_REVIEW
                (temp_dir / "manifest.json").write_text(json.dumps({"status": version.status.value, "version_id": version.version_id, "document_id": document_id, "policy_version": policy, "source_sha256": source_hash}, sort_keys=True) + "\n")
            version_artifact = {"version_id": version.version_id, "document_id": document_id, "policy_version": policy, "status": version.status.value, "source_sha256": source_hash, "pages": page_artifacts}
            (temp_dir / "version.json").write_text(json.dumps(version_artifact, ensure_ascii=False, sort_keys=True, indent=2) + "\n")
            response = {"schema_version": "1", "operation": "preprocess", "request_id": request_id, "version_id": version.version_id, "status": version.status.value, "pages": [{"page_number": item["page_number"], "status": item["status"], "attempts": 1, "findings": item.get("findings", [])} for item in page_artifacts], "page_summary": {"count": len(page_artifacts), "processed": len(page_artifacts), "validated": sum(item["status"] == "VALIDATED" for item in page_artifacts), "needs_review": sum(item["status"] == "NEEDS_REVIEW" for item in page_artifacts), "ready": sum(item["status"] == "VALIDATED" for item in page_artifacts)}, "artifacts": self._artifact_refs(temp_dir, ready), "manifest": manifest}
            (temp_dir / "response.json").write_text(json.dumps(response, ensure_ascii=False, sort_keys=True, indent=2) + "\n")
            version_dir = versions / version_id
            if version_dir.exists():
                raise ValueError("VERSION_ID_CONFLICT")
            temp_dir.rename(version_dir)
            if ready:
                root_stage = Path(tempfile.mkdtemp(prefix=".root-", dir=str(output)))
                try:
                    for name in ("chunks.jsonl", "manifest.json", "document_tree.json", "issues.jsonl"):
                        shutil.copy2(version_dir / name, root_stage / name)
                    for name in ("chunks.jsonl", "manifest.json", "document_tree.json", "issues.jsonl"):
                        os.replace(root_stage / name, output / name)
                    generations = output / "generations"
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
                    if root_stage.exists(): shutil.rmtree(root_stage)
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

    def get_status(self, version_id: str, artifact_root: str | Path) -> Mapping[str, Any]:
        if not re.fullmatch(r"[A-Za-z0-9._-]+", version_id):
            raise ValueError("INVALID_REQUEST")
        root = _canonical_path(str(artifact_root))
        root.mkdir(parents=True, exist_ok=True)
        with (root / ".preprocess.lock").open("a+") as lock:
            fcntl.flock(lock.fileno(), fcntl.LOCK_EX)
            try:
                path = root / "versions" / version_id / "response.json"
                if not path.exists():
                    raise ValueError("VERSION_NOT_FOUND")
                try:
                    response = json.loads(path.read_text())
                except json.JSONDecodeError as exc:
                    raise ValueError("VERSION_ARTIFACT_CORRUPT") from exc
                if not isinstance(response, dict) or not all(key in response for key in ("schema_version", "version_id", "status", "pages", "artifacts")) or not isinstance(response["pages"], list):
                    path.replace(path.with_name("response.corrupt.json"))
                    raise ValueError("VERSION_ARTIFACT_CORRUPT")
                if response["schema_version"] != "1" or response["version_id"] != version_id or response["status"] not in {"QUEUED", "PROCESSING", "VALIDATING", "READY", "NEEDS_REVIEW"}:
                    raise ValueError("VERSION_ARTIFACT_CORRUPT")
                for page in response["pages"]:
                    if not isinstance(page, dict) or not isinstance(page.get("page_number"), int) or page.get("status") not in {"VALIDATED", "NEEDS_REVIEW"} or not isinstance(page.get("attempts"), int) or not isinstance(page.get("findings"), list):
                        raise ValueError("VERSION_ARTIFACT_CORRUPT")
                for ref in response["artifacts"].values():
                    if isinstance(ref, dict) and "path" in ref and "sha256" in ref:
                        target = Path(ref["path"]).resolve()
                        if not (target.parent == path.parent or target.parent.parent.name == "generations") or not re.fullmatch(r"[0-9a-f]{64}", str(ref["sha256"])) or not target.exists() or _sha256(target) != ref["sha256"]:
                            raise ValueError("VERSION_ARTIFACT_CORRUPT")
                return {**response, "operation": "status"}
            finally:
                fcntl.flock(lock.fileno(), fcntl.LOCK_UN)

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
