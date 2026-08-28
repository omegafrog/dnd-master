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
from preprocessing_agent.domain.serialization import to_dict
from preprocessing_agent.layout import ReadingOrderPlanner
from preprocessing_agent.parsers.pdf import PdfDocumentParser, _is_reliable_native_table
from preprocessing_agent.pipeline.pipeline import PreprocessingPipeline
from preprocessing_agent.structure import HeadingAssociator, TableStructureDetector
from preprocessing_agent.ports.extraction import ExtractionCapabilityError, PageRenderPort, OcrPort, normalize_ocr_blocks
from preprocessing_agent.adapters.ocr import PyMuPdfPageRenderAdapter, TesseractOcrAdapter
from preprocessing_agent.validation import LayoutValidationService
from preprocessing_agent.pipeline.retry import PageRetryPolicy


class _RenderEvidenceSecondaryValidator:
    """Default independent adapter for rendered high-risk pages.

    It only attests that the renderer returned evidence; richer validators can
    be supplied through ``LayoutValidationService`` by application callers.
    """
    def validate(self, page: Mapping[str, Any], render_evidence: Mapping[str, Any]) -> bool:
        geometry = page.get("geometry", {})
        return bool(render_evidence.get("sha256") and render_evidence.get("width", 0) > 0
                    and render_evidence.get("height", 0) > 0
                    and abs(float(render_evidence["width"]) - float(geometry.get("width", 0))) < .01
                    and abs(float(render_evidence["height"]) - float(geometry.get("height", 0))) < .01)


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
                font_metadata = _font_metadata_by_block(page)
                for index, raw in enumerate(page.get_text("blocks")):
                    text = str(raw[4]).strip()
                    if not text:
                        continue
                    size, font = font_metadata.get(tuple(float(value) for value in raw[:4]), (None, None))
                    block = {"block_id": f"p{number}-b{index}", "text": text, "bbox": raw[:4]}
                    # Keep typography useful for heading confidence without
                    # turning page numbers and running headers (normally 8-9pt)
                    # into furniture merely because metadata is present.
                    if size is not None and size > 9:
                        block["font_size"] = size
                    if font is not None and ("bold" in font.casefold() or "semibold" in font.casefold()):
                        block["font_weight"] = "bold"
                    blocks.append(block)
                # PyMuPDF's table finder is the native structured extraction
                # seam. Keep cells as geometry-bearing blocks so downstream
                # structure detection receives real row/column provenance.
                try:
                    with contextlib.redirect_stdout(sys.stderr):
                        finder = page.find_tables()
                    found_tables = getattr(finder, "tables", finder or ())
                except (AttributeError, RuntimeError, TypeError):
                    found_tables = ()
                table_regions = []
                for table_index, table in enumerate(table for table in found_tables if _is_reliable_native_table(table)):
                    rows = getattr(table, "rows", ()) or ()
                    table_id = f"p{number}-table-{table_index}"
                    table_bbox = getattr(table, "bbox", None)
                    if table_bbox is not None and len(table_bbox) == 4:
                        table_regions.append(tuple(float(value) for value in table_bbox))
                    for row_index, row in enumerate(rows):
                        cells = getattr(row, "cells", row if isinstance(row, (list, tuple)) else ()) or ()
                        extracted = ()
                        try:
                            extracted = tuple(table.extract()[row_index])
                        except (AttributeError, IndexError, TypeError):
                            pass
                        for column_index, cell_bbox in enumerate(cells):
                            if cell_bbox is None or len(cell_bbox) != 4:
                                continue
                            text = str(extracted[column_index]).strip() if column_index < len(extracted) else ""
                            # A blank cell is still structural evidence; a
                            # single space satisfies the block value object
                            # while remaining absent from normalized prose.
                            text = text or " "
                            blocks.append({"block_id": f"{table_id}-r{row_index}-c{column_index}", "text": text,
                                           "bbox": tuple(cell_bbox), "kind": "table_cell", "table_id": table_id,
                                           "row": row_index, "column": column_index,
                                           "is_header": row_index == 0})
                if table_regions:
                    blocks = [block for block in blocks if block.get("kind") == "table_cell" or not _inside_table_region(block.get("bbox"), table_regions)]
                blocks = list(_deduplicate_blocks(blocks))
                pages.append({"page_number": number, "geometry": {"width": rect.width, "height": rect.height}, "blocks": blocks})
        return pages


def _font_metadata_by_block(page: Any) -> dict[tuple[float, float, float, float], tuple[float | None, str | None]]:
    """Preserve native heading signals alongside the compact block payload."""
    metadata: dict[tuple[float, float, float, float], tuple[float | None, str | None]] = {}
    payload = page.get_text("dict")
    if not isinstance(payload, Mapping):
        return metadata
    for raw in payload.get("blocks", []):
        lines = raw.get("lines", [])
        spans = [span for line in lines for span in line.get("spans", []) if str(span.get("text", "")).strip()]
        if not spans or raw.get("bbox") is None:
            continue
        first = spans[0]
        key = tuple(float(value) for value in raw["bbox"])
        metadata[key] = (float(first.get("size")) if first.get("size") is not None else None, str(first.get("font")) if first.get("font") else None)
    return metadata


def _inside_table_region(bbox: object, regions: list[tuple[float, float, float, float]]) -> bool:
    if not isinstance(bbox, (tuple, list)) or len(bbox) != 4:
        return False
    center_x = (float(bbox[0]) + float(bbox[2])) / 2
    center_y = (float(bbox[1]) + float(bbox[3])) / 2
    return any(x0 <= center_x <= x1 and y0 <= center_y <= y1 for x0, y0, x1, y1 in regions)


def _deduplicate_blocks(blocks: list[Mapping[str, Any]]) -> tuple[Mapping[str, Any], ...]:
    selected: dict[str, Mapping[str, Any]] = {}
    for block in blocks:
        block_id = str(block.get("block_id", ""))
        previous = selected.get(block_id)
        if previous is None or (
            str(previous.get("kind", "")).casefold() not in {"table", "table_cell", "cell"}
            and str(block.get("kind", "")).casefold() in {"table", "table_cell", "cell"}
        ):
            selected[block_id] = block
    return tuple(selected.values())


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
    def __init__(self, native_pdf: NativePdfPort | None = None, render: PageRenderPort | None = None, ocr: OcrPort | None = None) -> None:
        self.native_pdf = native_pdf or PyMuPdfNativePdfAdapter()
        self.render = render or PyMuPdfPageRenderAdapter()
        self.ocr = ocr or TesseractOcrAdapter()
        # Publication always requires a real renderer.  Fixtures and callers
        # may inject a renderer, but the default CLI path must not bypass it.
        # The production/default adapter path is strict.  Lightweight custom
        # extraction ports used by callers may provide their own render port;
        # legacy non-PDF fixtures have no renderable page and retain their
        # source evidence path.
        self._render_required = render is not None or native_pdf is None
        self.layout_validator = LayoutValidationService(
            secondary=_RenderEvidenceSecondaryValidator()
        )
        self._versions: dict[str, Mapping[str, Any]] = {}
        self.retry_policy = PageRetryPolicy()

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
        recovered = request.get("recovered_pages", {})
        try:
            raw_pages = self.native_pdf.extract(source) if source.suffix.lower() == ".pdf" else self._text_pages(source)
            if isinstance(recovered, Mapping):
                raw_pages = [recovered.get(str(p.get("page_number")), p) if isinstance(p, Mapping) else p for p in raw_pages]
            if not isinstance(raw_pages, (list, tuple)):
                raw_pages = [{"page_number": 1, "malformed": True}]
        except Exception as exc:
            if isinstance(recovered, Mapping) and recovered:
                raw_pages = list(recovered.values())
            else:
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
            heading_evidence: list[Any] = []
            table_evidence: list[Any] = []
            render_evidence: dict[str, Any] = {"page_number": position, "status": "UNAVAILABLE"}
            layout_validation: Mapping[str, Any] = {}
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
                if number != position:
                    raise ValueError("OUT_OF_ORDER_PAGE_METADATA")
                if number in seen_page_numbers:
                    raise ValueError("DUPLICATE_PAGE_NUMBER")
                seen_page_numbers.add(number)
                raw = self._augment_with_ocr(source, raw, number)
                if raw.get("capability_error"):
                    raise ValueError(str(raw["capability_error"]))
                geometry = PageGeometry(float(geometry_raw["width"]), float(geometry_raw["height"]))
                if not math.isfinite(geometry.width) or not math.isfinite(geometry.height):
                    raise ValueError("INVALID_GEOMETRY")
                blocks = []
                for block in raw.get("blocks", ()):
                    extraction_method = str(block.get("extraction_method", "native"))
                    if extraction_method not in {"native", "ocr", "hybrid"} or (extraction_method != "native" and not raw.get("ocr_enabled")):
                        raise ValueError("NON_NATIVE_EXTRACTION_UNSUPPORTED")
                    bbox = BoundingBox(*(float(item) for item in block["bbox"]))
                    if not all(math.isfinite(value) for value in (bbox.x0, bbox.y0, bbox.x1, bbox.y1)):
                        raise ValueError("INVALID_GEOMETRY")
                    if not geometry.contains(bbox):
                        raise ValueError("INVALID_GEOMETRY")
                    LayoutBlock(str(block.get("block_id", "")), str(block.get("kind", "text")), bbox, str(block.get("text", "")), extraction_method, float(block.get("confidence", 1.0)), document_id, number, geometry)
                    blocks.append({**block, "extraction_method": extraction_method, "text_confidence": float(block.get("text_confidence", block.get("confidence", 1.0))), "bbox": [bbox.x0, bbox.y0, bbox.x1, bbox.y1], "source_document_id": document_id, "page_number": number, "page_geometry": {"width": geometry.width, "height": geometry.height, "unit": geometry.unit, "origin": geometry.origin}})
                raw = {**raw, "blocks": blocks}
                layout_value = raw.get("layout")
                layout_name = layout_value if isinstance(layout_value, str) else str(layout_value.get("name", layout_value.get("strategy", ""))) if isinstance(layout_value, Mapping) else ""
                declared_multi = raw.get("column_count", 1) != 1 or layout_name in {"multi-column", "multi_column", "columns"} or raw.get("columns") not in (None, 1, [])
                if declared_multi and not blocks:
                    raise ValueError("MULTI_COLUMN_UNSUPPORTED: MULTI_COLUMN_GEOMETRY_REQUIRED")
                layout_plan = ReadingOrderPlanner().plan(blocks, geometry)
                if layout_plan.ambiguous:
                    raise ValueError("AMBIGUOUS_COLUMN_HYPOTHESIS")
                layout_evidence = to_dict(layout_plan)
                raw["layout"] = layout_evidence
                # Structure is page evidence, not flattened prose. An
                # irregular table remains reviewable and cannot be published.
                heading_evidence = to_dict(HeadingAssociator().associate(blocks, layout_plan))
                tables = TableStructureDetector().detect(blocks)
                table_evidence = to_dict(tables)
                table_findings = [finding for table in tables for finding in table.findings]
                if table_findings:
                    raise ValueError("IRREGULAR_TABLE: " + ",".join(sorted(set(table_findings))))
                raw["heading_associations"] = heading_evidence
                raw["tables"] = table_evidence
                render_evidence = self._render_evidence(source, number, geometry)
                raw["render_evidence"] = render_evidence
                validation = self.layout_validator.validate(raw, render_evidence)
                raw["layout_validation"] = validation.as_dict()
                layout_validation = validation.as_dict()
                if not validation.valid:
                    raise ValueError("LAYOUT_VALIDATION_FAILED: " + ",".join(item.code for item in validation.findings))
                version.record_page(PageExtraction.validated(number))
                parser_pages.append(raw)
                evidence = {"page_number": number, "page_classification": raw.get("page_classification", "text-native"), "geometry": {"width": geometry.width, "height": geometry.height, "unit": geometry.unit, "origin": geometry.origin}, "blocks": blocks, "layout": layout_evidence, "heading_associations": heading_evidence, "tables": table_evidence, "render_evidence": render_evidence, "layout_validation": validation.as_dict()}
                page_artifacts.append({**evidence, "status": PageStatus.VALIDATED.value, "evidence_sha256": hashlib.sha256(json.dumps(evidence, sort_keys=True).encode()).hexdigest()})
            except (KeyError, TypeError, ValueError) as exc:
                safe_number = number if "number" in locals() and 1 <= number <= version.page_count else position
                finding = str(exc) or "INVALID_GEOMETRY"
                findings = [item.strip() for item in finding.split(":") if item.strip()]
                version.record_page(PageExtraction(safe_number, PageStatus.NEEDS_REVIEW, findings))
                page_artifacts[:] = [item for item in page_artifacts if item.get("page_number") != safe_number]
                evidence = {"page_number": safe_number, "status": PageStatus.NEEDS_REVIEW.value, "findings": version.pages[safe_number].findings, "heading_associations": heading_evidence, "tables": table_evidence, "render_evidence": render_evidence, "layout_validation": layout_validation}
                page_artifacts.append({**evidence, "evidence_sha256": hashlib.sha256(json.dumps(evidence, sort_keys=True).encode()).hexdigest()})
        present_pages = {item["page_number"] for item in page_artifacts}
        for missing in range(1, version.page_count + 1):
            if missing not in present_pages:
                evidence = {"page_number": missing, "status": PageStatus.NEEDS_REVIEW.value, "findings": ["MISSING_PAGE_METADATA"]}
                page_artifacts.append({**evidence, "evidence_sha256": hashlib.sha256(json.dumps(evidence, sort_keys=True).encode()).hexdigest()})
        page_artifacts.sort(key=lambda item: item["page_number"])
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
                manifest = {"source": {"path": str(source), "sha256": source_hash}, "source_sha256": source_hash, "pipeline_version": policy, "schema_version": "1", "profile": "extraction-version", "policy": {"version": policy}, "statistics": {"pages": version.page_count}}
                (temp_dir / "manifest.json").write_text(json.dumps(manifest, sort_keys=True) + "\n")
            version_artifact = {"version_id": version.version_id, "document_id": document_id, "policy_version": policy, "page_count": version.page_count, "status": version.status.value, "source_sha256": source_hash, "pages": page_artifacts}
            (temp_dir / "version.json").write_text(json.dumps(version_artifact, ensure_ascii=False, sort_keys=True, indent=2) + "\n")
            response = {"schema_version": "1", "operation": "preprocess", "request_id": request_id, "version_id": version.version_id, "status": version.status.value, "pages": [{"page_number": item["page_number"], "status": item["status"], "attempts": 1, "findings": item.get("findings", []), "attempt_history": [{"attempt": 1, "status": item["status"], "findings": item.get("findings", [])}]} for item in page_artifacts], "page_summary": {"count": len(page_artifacts), "processed": len(page_artifacts), "validated": sum(item["status"] == "VALIDATED" for item in page_artifacts), "needs_review": sum(item["status"] == "NEEDS_REVIEW" for item in page_artifacts), "ready": sum(item["status"] == "VALIDATED" for item in page_artifacts)}, "artifacts": self._artifact_refs(temp_dir, ready), "manifest": manifest}
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
            response = {**response, "artifacts": self._artifact_refs(version_dir, ready)}
            if ready and 'generation' in locals():
                response = {**response, "artifacts": self._artifact_refs(generation, ready)}
            (version_dir / "response.json").write_text(json.dumps(response, ensure_ascii=False, sort_keys=True, indent=2) + "\n")
            if ready and 'generation' in locals():
                shutil.copy2(version_dir / "response.json", generation / "response.json")
                pointer = {"version_id": version_id, "generation": str(generation), "status": version.status.value}
                (output / "current.json.tmp").write_text(json.dumps(pointer, sort_keys=True) + "\n")
                (output / "current.json.tmp").replace(output / "current.json")
            else:
                # Keep the prior current generation visible; this attempt is version-scoped.
                pass
            index[idempotency_key] = version_id
            index_tmp = index_path.with_suffix(".tmp")
            index_tmp.write_text(json.dumps(index, sort_keys=True, indent=2) + "\n")
            os.replace(index_tmp, index_path)
            return response
        except Exception as exc:
            if temp_dir.exists(): shutil.rmtree(temp_dir)
            raise

    def _augment_with_ocr(self, source: Path, raw: Mapping[str, Any], number: int) -> Mapping[str, Any]:
        """Classify a page and OCR only declared image regions or image-only pages."""
        # Regional/multi-column analysis belongs to RAG-009; do not reinterpret
        # an explicitly declared layout as an OCR target here.
        layout_value = raw.get("layout")
        layout_name = layout_value if isinstance(layout_value, str) else str(layout_value.get("name", layout_value.get("strategy", ""))) if isinstance(layout_value, Mapping) else ""
        if raw.get("column_count", 1) != 1 or layout_name in {"multi-column", "multi_column", "columns"} or raw.get("columns") not in (None, 1, []):
            return {**raw, "page_classification": "ambiguous"}
        native_blocks = [dict(item) for item in raw.get("blocks", ()) if str(item.get("text", "")).strip()]
        image_regions = raw.get("image_regions", ())
        classification = "text-native" if native_blocks and not image_regions else ("mixed" if native_blocks else "image-only")
        updated = {**raw, "page_classification": classification}
        if classification == "text-native":
            return updated
        if self.ocr is None or not self._available(self.render) or not self._available(self.ocr):
            return {**updated, "capability_error": "OCR_UNAVAILABLE"}
        try:
            rendered = self.render.render(source, number)
            regions = list(image_regions) if image_regions else [None]
            ocr_blocks: list[Mapping[str, Any]] = []
            for region in regions:
                local_width = float(region[2] - region[0]) if region is not None else rendered.width
                local_height = float(region[3] - region[1]) if region is not None else rendered.height
                offset = None if getattr(self.ocr, "returns_page_coordinates", False) else region
                ocr_blocks.extend(normalize_ocr_blocks(self.ocr.recognize(rendered, region), page_number=number, width=rendered.width if offset is None else local_width, height=rendered.height if offset is None else local_height, offset=offset))
            return {**updated, "blocks": [*native_blocks, *ocr_blocks], "extraction_method": "hybrid" if native_blocks else "ocr", "ocr_enabled": True}
        except ExtractionCapabilityError as exc:
            return {**updated, "capability_error": exc.code}

    def _render_evidence(self, source: Path, number: int, geometry: PageGeometry) -> dict[str, Any]:
        """Capture a stable render proof when a renderer is supplied.

        The legacy native-only process remains usable with injected extraction
        fixtures; production callers opt into the strict render port by passing
        a renderer, in which case failures are explicit validation findings.
        """
        if not self._render_required:
            return {"page_number": number, "width": geometry.width, "height": geometry.height,
                    "source": "injected-extraction", "sha256": hashlib.sha256(
                        f"{number}:{geometry.width}:{geometry.height}".encode()).hexdigest()}
        if not self._available(self.render):
            raise ValueError("RENDER_VALIDATOR_UNAVAILABLE")
        if source.suffix.lower() != ".pdf":
            digest = hashlib.sha256(source.read_bytes()).hexdigest()
            return {"page_number": number, "width": geometry.width, "height": geometry.height,
                    "source": "text-source", "sha256": digest}
        try:
            rendered = self.render.render(source, number)
            image = rendered.image or b""
            return {"page_number": number, "width": float(rendered.width), "height": float(rendered.height),
                    "pixel_width": rendered.pixel_width, "pixel_height": rendered.pixel_height,
                    "media_type": rendered.media_type, "sha256": hashlib.sha256(image).hexdigest()}
        except Exception as exc:
            raise ValueError("RENDER_VALIDATION_FAILED") from exc

    @staticmethod
    def _available(adapter: Any) -> bool:
        check = getattr(adapter, "available", None)
        return bool(check()) if callable(check) else True

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
            try:
                pointer = json.loads(current.read_text()) if current.exists() else None
            except (OSError, json.JSONDecodeError) as exc:
                raise ValueError("VERSION_ARTIFACT_CORRUPT") from exc
            expected_generation = str(root / "generations" / version_id)
            if isinstance(pointer, dict) and pointer.get("version_id") == version_id and pointer.get("generation") in (None, expected_generation):
                selected = root / "generations" / version_id / "response.json"
            else:
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
                    snapshot_path = root / "versions" / version_id / "retry-state.json"
                    snapshot_version = None
                    if snapshot_path.is_file() and not snapshot_path.is_symlink():
                        snapshot = json.loads(snapshot_path.read_text())
                        if isinstance(snapshot, dict) and isinstance(snapshot.get("response"), dict):
                            response = snapshot["response"]
                            snapshot_version = snapshot.get("version")
                except json.JSONDecodeError as exc:
                    raise ValueError("VERSION_ARTIFACT_CORRUPT") from exc
                if not isinstance(response, dict) or not all(key in response for key in ("schema_version", "operation", "request_id", "version_id", "status", "pages", "page_summary", "artifacts", "manifest")) or not isinstance(response["pages"], list) or not isinstance(response["artifacts"], dict) or not isinstance(response["page_summary"], dict) or not isinstance(response["manifest"], dict):
                    raise ValueError("VERSION_ARTIFACT_CORRUPT")
                if response["schema_version"] != "1" or response["operation"] not in {"preprocess", "status", "retry_pages"} or not isinstance(response["request_id"], str) or not response["request_id"] or response["version_id"] != version_id or response["status"] not in {"QUEUED", "PROCESSING", "VALIDATING", "READY", "NEEDS_REVIEW"}:
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
                if len(set(page_numbers)) != len(page_numbers) or any(type(number) is not int or number < 1 for number in page_numbers) or page_numbers != sorted(page_numbers) or page_numbers != list(range(1, len(page_numbers) + 1)):
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
                if response["status"] == "READY" and not {"chunks", "document_tree", "issues"} <= set(response["artifacts"]):
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
                    version_data = snapshot_version if isinstance(snapshot_version, dict) else json.loads(Path(version_ref["path"]).read_text())
                    if manifest_data.get("source_sha256") != version_data.get("source_sha256") or version_data.get("version_id") != version_id or version_data.get("status") != response.get("status"):
                        raise ValueError("VERSION_ARTIFACT_CORRUPT")
                    if response.get("manifest") != manifest_data:
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

    def retry_pages(self, version_id: str, artifact_root: str | Path, pages: list[int], *, request_id: str = "retry") -> Mapping[str, Any]:
        """Re-extract, render and validate only selected review pages.

        The version remains quarantined until every page is validated.  A
        retry is persisted through a temp file replacement so an interrupted
        caller can safely resume from the last complete checkpoint.
        """
        root = _canonical_path(str(artifact_root)); _enforce_root(root, "PREPROCESS_ARTIFACT_ROOT")
        lock_path = root / ".preprocess.lock"
        with lock_path.open("a+") as lock:
            fcntl.flock(lock.fileno(), fcntl.LOCK_EX)
            current = self.get_status(version_id, root, _lock=False)
            if current["status"] == "READY":
                raise ValueError("PUBLISHED_VERSION_IMMUTABLE")
            wanted = sorted(set(int(p) for p in pages))
            by_number = {int(p["page_number"]): p for p in current["pages"]}
            if not wanted or any(p not in by_number for p in wanted):
                raise ValueError("INVALID_PAGE_SELECTION")
            index_path = root / "retry-index.json"
            try:
                retry_index = json.loads(index_path.read_text()) if index_path.exists() else {}
                if not isinstance(retry_index, dict): retry_index = {}
            except (OSError, json.JSONDecodeError):
                retry_index = {}
            idem = f"{version_id}:{request_id}:{','.join(map(str, wanted))}"
            if idem in retry_index:
                saved = retry_index[idem]
                if isinstance(saved, dict) and saved.get("result_version_id"):
                    return self.get_status(str(saved["result_version_id"]), root, _lock=False)
                # A prior process may have been interrupted after page
                # checkpointing but before publication; continue below.
            persisted_recovered = {}
            snapshot_path = root / "versions" / version_id / "retry-state.json"
            if snapshot_path.is_file():
                try:
                    data = json.loads(snapshot_path.read_text())
                    persisted_recovered = dict(data.get("recovered_pages", {}))
                    if idem not in retry_index and data.get("idempotency") == idem:
                        # The snapshot is authoritative if the process died
                        # before retry-index replacement.
                        retry_index[idem] = {"version_id": version_id, "pages": wanted, "state": "promotion_pending"}
                except (OSError, json.JSONDecodeError, TypeError):
                    pass
            resume_promotion = isinstance(retry_index.get(idem), dict) and retry_index[idem].get("state") == "promotion_pending" and all(p.get("status") == "VALIDATED" for p in current["pages"])
            updated = []
            recovered_pages: dict[str, Mapping[str, Any]] = dict(persisted_recovered)
            source_path = Path(str(current.get("manifest", {}).get("source", {}).get("path", "")))
            for page in (() if resume_promotion else current["pages"]):
                item = dict(page)
                if item["page_number"] in wanted:
                    if item.get("status") == "VALIDATED":
                        if str(item["page_number"]) in persisted_recovered:
                            updated.append(item)
                            continue
                        raise ValueError("VALIDATED_PAGE_IMMUTABLE")
                    attempt = self.retry_policy.request(item)
                    history = list(item.get("attempt_history", [])); history.append(attempt.as_dict())
                    item["attempts"] = attempt.attempt_number; item["attempt_history"] = history
                    item["diagnostics"] = {"strategy": attempt.strategy, "regions": [list(r) for r in attempt.regions], "findings": list(attempt.findings)}
                    # Re-run the native adapter for this page only.  A failed
                    # extraction remains NEEDS_REVIEW and is never guessed
                    # into READY.
                    try:
                        if not source_path.is_file(): raise ValueError("SOURCE_NOT_FOUND")
                        raw_pages = self.native_pdf.extract(source_path)
                        raw = next((p for p in raw_pages if isinstance(p, Mapping) and p.get("page_number") == item["page_number"]), None)
                        geometry = raw.get("geometry", {}) if isinstance(raw, Mapping) else {}
                        boxes = raw.get("blocks", ()) if isinstance(raw, Mapping) else ()
                        valid = isinstance(raw, Mapping) and float(geometry.get("width", 0)) > 0 and float(geometry.get("height", 0)) > 0 and all(isinstance(b, Mapping) and len(b.get("bbox", ())) == 4 and 0 <= float(b["bbox"][0]) <= float(b["bbox"][2]) <= float(geometry["width"]) and 0 <= float(b["bbox"][1]) <= float(b["bbox"][3]) <= float(geometry["height"]) for b in boxes)
                        if valid:
                            page_geometry = PageGeometry(float(geometry["width"]), float(geometry["height"]))
                            layout_plan = ReadingOrderPlanner().plan(boxes, page_geometry)
                            if layout_plan.ambiguous:
                                valid = False
                            raw = {**raw, "layout": to_dict(layout_plan)}
                            raw["heading_associations"] = to_dict(HeadingAssociator().associate(boxes, layout_plan))
                            raw["tables"] = to_dict(TableStructureDetector().detect(boxes))
                            render = self._render_evidence(source_path, item["page_number"], page_geometry)
                            validation = self.layout_validator.validate(raw, render)
                            valid = validation.valid
                            if valid:
                                recovered_pages[str(item["page_number"])] = raw
                        if valid:
                            item["status"] = "VALIDATED"; item["findings"] = []
                            history[-1] = {**history[-1], "status": "VALIDATED", "findings": []}
                        else:
                            item["findings"] = ["RETRY_EXTRACTION_FAILED"]
                    except Exception as exc:
                        item["findings"] = [str(exc) or "RETRY_EXTRACTION_FAILED"]
                    item["diagnostics"]["finding_regions"] = item.get("finding_regions", item["diagnostics"].get("regions", []))
                    item["diagnostics"]["overlay"] = f"diagnostics/{version_id}/page-{item['page_number']}-attempt-{attempt.attempt_number}.json"
                updated.append(item)
            if resume_promotion:
                updated = [dict(p) for p in current["pages"]]
            response = {**current, "operation": "retry_pages", "request_id": request_id, "pages": updated}
            statuses = [p["status"] for p in updated]
            response["page_summary"] = {**current["page_summary"], "validated": statuses.count("VALIDATED"), "needs_review": statuses.count("NEEDS_REVIEW"), "ready": statuses.count("VALIDATED")}
            version_dir = root / "versions" / version_id
            response_path = version_dir / "response.json"
            if response_path.is_symlink() or not response_path.exists():
                raise ValueError("VERSION_ARTIFACT_CORRUPT")
            # Publish the complete retry snapshot before exposing either
            # mutable read-model file; recovery can therefore finish from a
            # single authoritative record after any interruption.
            version_path = version_dir / "version.json"
            snapshot = version_dir / "retry-state.json.tmp"
            snapshot.write_text(json.dumps({"response": response, "version": json.loads(version_path.read_text()) if version_path.exists() else {},
                                            "idempotency": idem, "recovered_pages": recovered_pages}, ensure_ascii=False,
                                 sort_keys=True, indent=2) + "\n")
            with snapshot.open("rb") as stream: os.fsync(stream.fileno())
            os.replace(snapshot, version_dir / "retry-state.json")
            staged = response_path.with_suffix(".json.tmp")
            staged.write_text(json.dumps(response, ensure_ascii=False, sort_keys=True, indent=2) + "\n")
            os.replace(staged, response_path)
            if version_path.exists():
                version_data = json.loads(version_path.read_text())
                version_data["pages"] = [{k: v for k, v in item.items() if k in {"page_number", "status", "findings", "attempts", "attempt_history", "diagnostics"}} for item in updated]
                version_tmp = version_path.with_suffix(".json.tmp")
                version_tmp.write_text(json.dumps(version_data, ensure_ascii=False, sort_keys=True, indent=2) + "\n")
                os.replace(version_tmp, version_path)
                # The version artifact is content-addressed; refresh its ref
                # after the atomic page read-model update.
                refs = dict(response.get("artifacts", {}))
                if isinstance(refs.get("version"), dict):
                    refs["version"] = {"path": str(version_path), "sha256": _sha256(version_path)}
                response["artifacts"] = refs
                response_tmp = response_path.with_suffix(".json.tmp")
                response_tmp.write_text(json.dumps(response, ensure_ascii=False, sort_keys=True, indent=2) + "\n")
                os.replace(response_tmp, response_path)
                # Keep one recoverable retry snapshot beside the pair.  The
                # snapshot is written last and can be used to reconcile an
                # interrupted response/version replacement.
                snapshot = version_dir / "retry-state.json.tmp"
                snapshot.write_text(json.dumps({"response": response, "version": version_data,
                                                "idempotency": idem, "recovered_pages": recovered_pages}, ensure_ascii=False,
                                       sort_keys=True, indent=2) + "\n")
                with snapshot.open("rb") as stream:
                    os.fsync(stream.fileno())
                os.replace(snapshot, version_dir / "retry-state.json")
            diag_dir = root / "diagnostics" / version_id; diag_dir.mkdir(parents=True, exist_ok=True)
            for item in updated:
                if item["page_number"] in wanted:
                    stem = diag_dir / f"page-{item['page_number']}-attempt-{item['attempts']}"
                    (stem.with_suffix(".json.tmp")).write_text(json.dumps(item.get("diagnostics", {}), sort_keys=True) + "\n")
                    os.replace(stem.with_suffix(".json.tmp"), stem.with_suffix(".json"))
                    regions = item.get("diagnostics", {}).get("finding_regions", [])
                    rects = "".join(f'<rect x="{r[0]}" y="{r[1]}" width="{max(0,r[2]-r[0])}" height="{max(0,r[3]-r[1])}" fill="none" stroke="red"/>' for r in regions if isinstance(r, (list, tuple)) and len(r) == 4)
                    svg = f'<svg xmlns="http://www.w3.org/2000/svg" width="1000" height="1400">{rects}<text x="8" y="20">page {item["page_number"]} attempt {item["attempts"]}</text></svg>\n'
                    (stem.with_suffix(".svg.tmp")).write_text(svg)
                    os.replace(stem.with_suffix(".svg.tmp"), stem.with_suffix(".svg"))
            retry_index[idem] = {"version_id": version_id, "pages": wanted, "state": "promotion_pending"}
            retry_tmp = index_path.with_suffix(".tmp"); retry_tmp.write_text(json.dumps(retry_index, sort_keys=True) + "\n"); os.replace(retry_tmp, index_path)
            # Once every page is validated, materialize a fresh published
            # version through the normal full pipeline.  The quarantined
            # source version is never mutated into READY and remains an audit
            # checkpoint; generation/current publication is publish-last.
            if all(item.get("status") == "VALIDATED" for item in updated):
                manifest_source = current.get("manifest", {}).get("source", {})
                promoted_id = f"{version_id}-retry-{hashlib.sha256(idem.encode()).hexdigest()[:8]}"
                promoted_request = {"request_id": f"{request_id}-publish", "source_path": manifest_source.get("path"),
                                    "source_sha256": manifest_source.get("sha256"),
                                    "policy_version": current.get("manifest", {}).get("policy", {}).get("version", "retry"),
                                    "output_dir": str(root), "version_id": promoted_id,
                                    "recovered_pages": recovered_pages}
                try:
                    promoted = self._preprocess_locked(promoted_request, root)
                    if promoted.get("status") == "READY":
                        retry_index[idem] = {"version_id": version_id, "pages": wanted, "state": "completed", "result_version_id": promoted["version_id"]}
                    else:
                        retry_index[idem] = {"version_id": version_id, "pages": wanted, "state": "promotion_pending"}
                    retry_tmp = index_path.with_suffix(".tmp"); retry_tmp.write_text(json.dumps(retry_index, sort_keys=True) + "\n"); os.replace(retry_tmp, index_path)
                    return {**promoted, "operation": "retry_pages", "request_id": request_id,
                            "retry_version_id": version_id}
                except Exception:
                    # Keep the page checkpoint available for a later resume;
                    # publication failure must not expose a partial READY root.
                    pass
            return response

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
