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
import logging
import time
from collections.abc import Sequence
from dataclasses import dataclass, field
from enum import Enum
from pathlib import Path
from typing import Any, Mapping, Protocol

from preprocessing_agent.domain import PageStatus, ParsedDocument, ParsedPage, ParsedBlock, SourceSpan
from preprocessing_agent.domain.layout import BoundingBox, PageGeometry, LayoutBlock
from preprocessing_agent.domain.serialization import to_dict
from preprocessing_agent.layout import ReadingOrderPlanner
from preprocessing_agent.layout.page_classification import form_content_state, form_geometry_counts, is_form_like
from preprocessing_agent.layout.table_candidate_scoring import TABLE_SCREEN_DPI, assess_table_candidates
from preprocessing_agent.parsers.pdf import PdfDocumentParser, _classify_text_detection_hypothesis, _find_native_tables, _native_table_candidate_diagnostic, _reconcile_native_table_candidates, _reconstruct_native_table, _without_consumed_header_blocks
from preprocessing_agent.pipeline.pipeline import PreprocessingPipeline
from preprocessing_agent.structure import HeadingAssociator, TableStructureDetector, document_reference_font_size
from preprocessing_agent.ports.extraction import ExtractionCapabilityError, PageRenderPort, OcrPort, normalize_ocr_blocks
from preprocessing_agent.adapters.ocr import PyMuPdfPageRenderAdapter, TesseractOcrAdapter
from preprocessing_agent.validation import LayoutValidationService
from preprocessing_agent.pipeline.retry import PageRetryPolicy

_LOGGER = logging.getLogger(__name__)
_LAYOUT_ANALYSIS_VERSION = "reading-order-v2"


def _page_layout_fingerprint(page: Mapping[str, Any]) -> str:
    geometry = page.get("geometry", {})
    blocks = [{"block_id": str(block.get("block_id", "")), "kind": block.get("kind", "text"),
               "table_id": block.get("table_id"), "row": block.get("row"), "column": block.get("column"),
               "extraction_method": block.get("extraction_method", "native"),
               "text_sha256": hashlib.sha256(str(block.get("text", "")).encode()).hexdigest(),
               "bbox": [float(value) for value in block.get("bbox", ())]}
              for block in page.get("blocks", ()) if isinstance(block, Mapping)]
    normalized_geometry = {key: float(geometry[key]) for key in ("width", "height")}
    payload = json.dumps({"geometry": normalized_geometry, "blocks": blocks}, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(payload.encode()).hexdigest()


def _layout_approval_key(source_sha256: str, page_number: int, page: Mapping[str, Any]) -> str:
    return hashlib.sha256(f"{source_sha256}:{page_number}:{_page_layout_fingerprint(page)}:{_LAYOUT_ANALYSIS_VERSION}".encode()).hexdigest()


def _layout_approval_root(output: Path) -> Path:
    parent_name = output.parent.name
    return output.parent.parent if re.fullmatch(r"[0-9a-fA-F-]{36}", parent_name) else output.parent


def _load_layout_approval(output: Path, source_sha256: str, page_number: int,
                          page: Mapping[str, Any]) -> Mapping[str, Any] | None:
    common_root = _layout_approval_root(output)
    key = _layout_approval_key(source_sha256, page_number, page)
    lock_path = common_root / ".layout-approvals.lock"
    with lock_path.open("a+") as lock:
        fcntl.flock(lock.fileno(), fcntl.LOCK_SH)
        try:
            registry_path = common_root / "layout-approvals.json"
            registry = json.loads(registry_path.read_text()) if registry_path.exists() else {}
            value = registry.get(key) if isinstance(registry, Mapping) else None
            if (not isinstance(value, Mapping) or not isinstance(value.get("admin_id"), str)
                    or not value["admin_id"] or not isinstance(value.get("candidate_version"), str)
                    or not value["candidate_version"] or not isinstance(value.get("selections"), Mapping)
                    or not value["selections"] or value.get("source_sha256") != source_sha256
                    or value.get("page_layout_fingerprint") != _page_layout_fingerprint(page)
                    or value.get("layout_analysis_version") != _LAYOUT_ANALYSIS_VERSION):
                return None
            return value
        except (OSError, json.JSONDecodeError):
            return None
        finally:
            fcntl.flock(lock.fileno(), fcntl.LOCK_UN)


def _save_layout_approval(output: Path, source_sha256: str, page_number: int,
                          page: Mapping[str, Any], confirmation: Mapping[str, Any]) -> None:
    common_root = _layout_approval_root(output)
    common_root.mkdir(parents=True, exist_ok=True)
    key = _layout_approval_key(source_sha256, page_number, page)
    lock_path = common_root / ".layout-approvals.lock"
    with lock_path.open("a+") as lock:
        fcntl.flock(lock.fileno(), fcntl.LOCK_EX)
        try:
            registry_path = common_root / "layout-approvals.json"
            registry = json.loads(registry_path.read_text()) if registry_path.exists() else {}
            if not isinstance(registry, dict):
                registry = {}
            registry[key] = dict(confirmation)
            staged = registry_path.with_suffix(".json.tmp")
            staged.write_text(json.dumps(registry, sort_keys=True) + "\n")
            os.replace(staged, registry_path)
        finally:
            fcntl.flock(lock.fileno(), fcntl.LOCK_UN)


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
    PARTIAL = "PARTIAL"
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
        if self.status in {ExtractionStatus.READY, ExtractionStatus.PARTIAL}:
            raise ValueError("published extraction version is immutable")
        if not 1 <= page.page_number <= self.page_count:
            raise ValueError("page number is outside extraction version")
        self.pages[page.page_number] = page
        self.status = ExtractionStatus.NEEDS_REVIEW if any(item.status == PageStatus.NEEDS_REVIEW for item in self.pages.values()) else ExtractionStatus.VALIDATING
        if page.status == PageStatus.NEEDS_REVIEW:
            self.status = ExtractionStatus.NEEDS_REVIEW

    def publish(self) -> None:
        usable = {PageStatus.VALIDATED, PageStatus.USABLE_WITH_WARNINGS}
        if (len(self.pages) != self.page_count
                or (not any(page.status in usable for page in self.pages.values())
                    and not all(page.status == PageStatus.EXCLUDED_FORM for page in self.pages.values()))
                or any(page.status not in {*usable, PageStatus.EXCLUDED_FORM, PageStatus.NEEDS_REVIEW}
                       for page in self.pages.values())):
            self.status = ExtractionStatus.NEEDS_REVIEW
            raise ValueError("cannot publish extraction version without usable page content")
        self.status = (ExtractionStatus.PARTIAL
                       if any(page.status in {PageStatus.USABLE_WITH_WARNINGS, PageStatus.NEEDS_REVIEW}
                              for page in self.pages.values())
                       else ExtractionStatus.READY)


class NativePdfPort(Protocol):
    def extract(self, source: Path) -> list[Mapping[str, Any]]: ...


class PyMuPdfNativePdfAdapter:
    provider_name = "pymupdf"

    def __init__(self, *, analyze_structure: bool = True, table_extractor: Any | None = None) -> None:
        self.analyze_structure = analyze_structure
        if table_extractor is None:
            from preprocessing_agent.adapters.llm_table_extractor import AiProviderTableExtractor
            table_extractor = AiProviderTableExtractor.from_environment()
        self.table_extractor = table_extractor

    def extract(self, source: Path, *, page_numbers: Sequence[int] | None = None) -> list[Mapping[str, Any]]:
        try:
            with contextlib.redirect_stdout(sys.stderr):
                import fitz  # type: ignore
        except ImportError as exc:
            raise RuntimeError("PyMuPDF is required for PDF process requests") from exc
        pages: list[Mapping[str, Any]] = []
        pending_screen_pages: list[tuple[Any, ...]] = []
        with fitz.open(source) as document:
            for number, page in enumerate(document, 1):
                if page_numbers is not None and number not in page_numbers:
                    continue
                rect = page.rect
                blocks = []
                image_regions = [tuple(float(value) for value in image["bbox"])
                                 for image in page.get_image_info(xrefs=True)
                                 if isinstance(image.get("bbox"), (list, tuple)) and len(image["bbox"]) == 4]
                widgets = list(page.widgets() or ())
                populated_widgets = [widget for widget in widgets
                                     if str(getattr(widget, "field_value", "") or "").strip()]
                font_metadata = _font_metadata_by_block(page) if self.analyze_structure else {}
                text_spans = {}
                text_lines = {}
                scoring_blocks = []
                text_payload = page.get_text("dict")
                text_blocks = text_payload.get("blocks", ()) if isinstance(text_payload, Mapping) else ()
                for text_block in text_blocks:
                    key = tuple(float(value) for value in text_block.get("bbox", ()))
                    detailed_spans = []
                    detailed_lines = []
                    for line_index, line in enumerate(text_block.get("lines", ())):
                        raw_spans = line.get("spans", ())
                        line_parts: list[str] = []
                        previous_text = ""
                        for span in raw_spans:
                            span_text = str(span.get("text", ""))
                            if span_text and previous_text and not previous_text[-1:].isspace() and not span_text[:1].isspace():
                                line_parts.append(" ")
                            line_parts.append(span_text)
                            if span_text:
                                previous_text = span_text
                        line_text = "".join(line_parts)
                        line_spans = []
                        for span_index, span in enumerate(raw_spans):
                            font_name = str(span.get("font", ""))
                            item = {"text": span.get("text", ""), "bbox": span.get("bbox"),
                                "font_size": span.get("size"), "size": span.get("size"),
                                "font": font_name,
                                "font_weight": "bold" if "bold" in font_name.casefold() or "semibold" in font_name.casefold() else font_name,
                                "span_index": span_index}
                            line_spans.append(item)
                            detailed_spans.append(item)
                        detailed_lines.append({"text": line_text, "bbox": line.get("bbox"),
                            "line_index": line_index, "spans": line_spans})
                    text_spans[key] = detailed_spans
                    text_lines[key] = detailed_lines
                for index, raw in enumerate(page.get_text("blocks")):
                    text = str(raw[4]).strip()
                    if not text:
                        continue
                    size, font = font_metadata.get(tuple(float(value) for value in raw[:4]), (None, None))
                    block = {"block_id": f"p{number}-b{index}", "text": text, "bbox": raw[:4]}
                    block_lines = text_lines.get(tuple(float(value) for value in raw[:4]), [])
                    scoring_blocks.append({**block, "lines": block_lines})
                    if self.analyze_structure:
                        block.update(spans=text_spans.get(tuple(float(value) for value in raw[:4]), []),
                                     lines=block_lines)
                    # Keep typography useful for heading confidence without
                    # turning page numbers and running headers (normally 8-9pt)
                    # into furniture merely because metadata is present.
                    if size is not None and size > 9:
                        block["font_size"] = size
                    if font is not None and ("bold" in font.casefold() or "semibold" in font.casefold()):
                        block["font_weight"] = "bold"
                    blocks.append(block)
                for index, widget in enumerate(populated_widgets):
                    value = str(getattr(widget, "field_value", "") or "").strip()
                    field_rect = getattr(widget, "rect", None)
                    if value and field_rect is not None:
                        blocks.append({"block_id": f"p{number}-field-{index}", "text": value,
                                       "bbox": [float(field_rect.x0), float(field_rect.y0),
                                                float(field_rect.x1), float(field_rect.y1)],
                                       "kind": "form_value"})
                # PyMuPDF's table finder is the native structured extraction
                # seam. Keep cells as geometry-bearing blocks so downstream
                # structure detection receives real row/column provenance.
                native_candidates = (_find_native_tables(page, number)
                                     if self.analyze_structure or self.table_extractor is not None else [])
                found_tables = native_candidates if self.analyze_structure else []
                reconstructed = []
                reconstruction_elapsed = []
                for table, strategy in found_tables:
                    reconstruction_started = time.monotonic()
                    projected, diagnostic = _reconstruct_native_table(table, blocks, number)
                    diagnostic.update(detection_strategies=strategy.split("+"),
                                      detection_deduplicated="+" in strategy)
                    _classify_text_detection_hypothesis(diagnostic, strategy, number)
                    reconstructed.append((projected, diagnostic))
                    reconstruction_elapsed.append(round((time.monotonic() - reconstruction_started) * 1000))
                selected_tables, table_diagnostics = _reconcile_native_table_candidates(found_tables, reconstructed, number)
                for index, diagnostic in enumerate(table_diagnostics):
                    table_bbox = diagnostic.get("effective_bbox") or diagnostic["bbox"]
                    source_spans = [span for block in blocks for span in block.get("spans", ())
                                    if isinstance(span, Mapping) and isinstance(span.get("bbox"), (list, tuple))
                                    and len(span["bbox"]) == 4 and table_bbox
                                    and table_bbox[0] <= (float(span["bbox"][0]) + float(span["bbox"][2])) / 2 <= table_bbox[2]
                                    and table_bbox[1] <= (float(span["bbox"][1]) + float(span["bbox"][3])) / 2 <= table_bbox[3]]
                    projected = reconstructed[index][0]
                    mapping_tolerance = diagnostic.get("mapping_tolerance", 0.0)
                    mapped_count = sum(sum(cell["bbox"][0] - mapping_tolerance <= (float(span["bbox"][0]) + float(span["bbox"][2])) / 2 <= cell["bbox"][2] + mapping_tolerance
                                           and cell["bbox"][1] - mapping_tolerance <= (float(span["bbox"][1]) + float(span["bbox"][3])) / 2 <= cell["bbox"][3] + mapping_tolerance
                                           for cell in projected) == 1 for span in source_spans)
                    _dev_log("dev_preprocessing_layout stage=native_table_reconstruction pageNumber=%d candidateIndex=%d strategies=%s detectionDeduplicated=%s rowCount=%d reconstructedRowCount=%d columnCount=%d expectedCellCount=%d actualProjectedCellCount=%d sourceSpanCount=%d mappedSpanCount=%d sourceTokenCount=%d projectedTokenCount=%d textMatchesGrid=%s emptyRowCount=%d alignedFollowingSpanCount=%d continuedRowProjected=%s elapsedMs=%d decision=%s rejectionReason=%s recoveryAction=%s outcome=%s",
                             number, index, "+".join(diagnostic["detection_strategies"]), diagnostic["detection_deduplicated"], diagnostic["row_count"], diagnostic.get("reconstructed_row_count", diagnostic["row_count"]), diagnostic["column_count"],
                             diagnostic["expected_cell_count"], len(projected), len(source_spans), mapped_count,
                             diagnostic.get("source_token_count", 0), diagnostic.get("projected_token_count", 0),
                             diagnostic.get("source_text_matches_grid", False),
                             diagnostic.get("extracted_empty_row_count", 0), diagnostic.get("aligned_following_span_count", 0),
                             diagnostic.get("continued_row_projected", False),
                             reconstruction_elapsed[index],
                             diagnostic["decision"], diagnostic["rejection_reason"] or "none",
                             "needs_review" if diagnostic["decision"] == "strong_unresolved" else "continue_layout_analysis",
                             "accepted" if diagnostic["decision"] == "confirmed_structured" else diagnostic["decision"])
                table_regions = []
                for table_index, (table, _, projected, diagnostic) in enumerate(selected_tables):
                    if diagnostic["decision"] != "confirmed_structured":
                        continue
                    blocks = _without_consumed_header_blocks(blocks, projected)
                    table_id = f"p{number}-table-{table_index}"
                    table_bbox = diagnostic.get("effective_bbox") or getattr(table, "bbox", None)
                    if table_bbox is not None and len(table_bbox) == 4:
                        table_regions.append(tuple(float(value) for value in table_bbox))
                    for cell in projected:
                        blocks.append({**cell, "block_id": f"{table_id}-r{cell['row']}-c{cell['column']}",
                                       "table_id": table_id})
                if table_regions:
                    blocks = [block for block in blocks if block.get("kind") == "table_cell" or not _inside_table_region(block.get("bbox"), table_regions)]
                blocks = list(_deduplicate_blocks(blocks))
                text_line_count = sum(len(block.get("lines", ())) for block in text_blocks)
                drawings = list(page.get_drawings() or ())
                form_diagnostics = form_geometry_counts(drawings, text_line_count)
                page_classification = "form" if is_form_like(**form_diagnostics) else "text-native"
                previous_page = pages[-1] if pages else {}
                previous_native_tables = previous_page.get("native_table_diagnostics", ())
                previous_confirmed_native_table = any(
                    isinstance(item, Mapping)
                    and item.get("decision") in {"confirmed_structured", "strong_unresolved"}
                    for item in previous_native_tables
                ) if isinstance(previous_native_tables, (list, tuple)) else False
                assessment = (assess_table_candidates(
                    blocks=scoring_blocks,
                    lines=[line for values in text_lines.values() for line in values],
                    drawings=drawings,
                    image_regions=image_regions,
                    native_candidates=native_candidates,
                    width=float(rect.width), height=float(rect.height), page_number=number,
                    previous_page_had_table=bool(previous_confirmed_native_table or
                        previous_page.get("table_extraction_diagnostics", {}).get("table_count", 0)),
                ) if self.table_extractor is not None and page_classification != "form" else None)
                table_extraction_diagnostics: dict[str, Any] = {
                    "status": ("skipped_form" if page_classification == "form" else
                               "pending" if self.table_extractor is not None else "not_configured"),
                    "table_count": 0, "cell_count": 0,
                }
                if assessment is not None:
                    table_extraction_diagnostics["candidate_assessment"] = assessment
                    _dev_log("dev_preprocessing_table_score stage=page_assessed pageNumber=%d regionCount=%d nativeCandidateCount=%d expectedExtractionRegionCount=local_scored_regions actualExtractionRegionCount=%d expectedScoreAtLeast=%d actualScore=%d thresholdForScreening=%d components=%s penalties=%s exceptions=%s route=%s recoveryAction=%s outcome=assessed",
                             number, assessment["region_count"], len(native_candidates),
                             len(assessment.get("extraction_regions", ())),
                             assessment["policy"]["extract_threshold"], assessment["score"],
                             assessment["policy"]["screen_threshold"],
                             assessment["components"], assessment["penalties"], assessment["exceptions"],
                             assessment["route"], "extract_region" if assessment["route"] == "extract" else
                             "screen_region" if assessment["route"] == "screen" else "skip_image_processing")
                elif page_classification != "form" and self.table_extractor is None:
                    _dev_log("dev_preprocessing_table_llm stage=page_skipped pageNumber=%d expectedConfiguredProvider=openai_compatible actualConfiguredProvider=none route=pymupdf_text_only recoveryAction=record_page_warning outcome=skipped",
                             number)
                if page_classification == "form":
                    _dev_log("dev_preprocessing_table_llm stage=page_skipped pageNumber=%d expectedPageType=non_form actualPageType=form route=preserve_form_asset recoveryAction=skip_table_model_call outcome=skipped",
                             number)
                _dev_log("dev_preprocessing_layout stage=page_classification pageNumber=%d drawingCount=%d alignedLineCount=%d longHorizontalLineCount=%d textLineCount=%d expectedFormEvidence=repeated_writing_rules actualForm=%s route=%s outcome=%s",
                         number, form_diagnostics["drawing_count"], form_diagnostics["aligned_line_count"],
                         form_diagnostics["long_horizontal_line_count"], form_diagnostics["text_line_count"], page_classification == "form",
                         "form_review" if page_classification == "form" else "prose_layout", page_classification)
                page_result = {"page_number": number, "geometry": {"width": rect.width, "height": rect.height},
                               "blocks": blocks, "page_classification": page_classification,
                               "form_diagnostics": form_diagnostics,
                               "form_content_state": form_content_state(
                                   populated_field_count=len(populated_widgets),
                                   image_region_count=len(image_regions),
                                   text_block_count=sum(bool(str(block.get("text", "")).strip()) for block in blocks)),
                               "populated_form_field_count": len(populated_widgets),
                               "image_regions": image_regions,
                               "table_extraction_diagnostics": table_extraction_diagnostics}
                if self.analyze_structure:
                    page_result.update(native_table_candidate_count=len(native_candidates),
                                       native_table_diagnostics=table_diagnostics)
                pages.append(page_result)
                if assessment is not None and assessment["route"] == "extract":
                    started = time.monotonic()
                    try:
                        _extract_vision_candidate_region(page_result, page, assessment, number,
                                                         rect.width, rect.height, self.table_extractor, fitz)
                        page_result["table_extraction_diagnostics"]["candidate_screen"] = "high_score_direct"
                    except Exception as exc:
                        table_extraction_diagnostics.update(
                            status="failed", failure_code=(str(exc) if str(exc).startswith("TABLE_LLM_")
                                                            else type(exc).__name__),
                            candidate_screen="high_score_direct_extraction_failed")
                        _dev_log("dev_preprocessing_table_extract stage=direct_region_failed pageNumber=%d expectedScoreAtLeast=%d actualScore=%d failureType=%s elapsedMs=%d route=pymupdf_text_only recoveryAction=preserve_text_and_warn outcome=partial",
                                 number, assessment["policy"]["extract_threshold"], assessment["score"],
                                 type(exc).__name__, round((time.monotonic() - started) * 1000))
                elif assessment is not None and assessment["route"] == "screen":
                    started = time.monotonic()
                    try:
                        clip = _candidate_clip(assessment, rect.width, rect.height, fitz)
                        thumbnail = page.get_pixmap(matrix=fitz.Matrix(TABLE_SCREEN_DPI / 72, TABLE_SCREEN_DPI / 72),
                                                    clip=clip, alpha=False)
                        pending_screen_pages.append((page_result, thumbnail.tobytes("png"), thumbnail.width,
                                                     thumbnail.height, number, rect.width, rect.height,
                                                     bool(native_candidates), assessment))
                        _dev_log("dev_preprocessing_table_screen stage=region_rendered pageNumber=%d expectedDpi=%d actualPixelWidth=%d actualPixelHeight=%d regionBbox=%s elapsedMs=%d route=low_resolution_visual_screen outcome=ready",
                                 number, TABLE_SCREEN_DPI, thumbnail.width, thumbnail.height,
                                 assessment["region_bbox"], round((time.monotonic() - started) * 1000))
                    except Exception as exc:
                        table_extraction_diagnostics.update(status="failed", failure_code=(str(exc) if str(exc).startswith("TABLE_LLM_") else type(exc).__name__),
                                                            candidate_screen="region_render_failed")
                        _dev_log("dev_preprocessing_table_screen stage=region_render_failed pageNumber=%d expectedDpi=%d actualRegion=%s failureType=%s elapsedMs=%d route=pymupdf_text_only recoveryAction=preserve_text_and_warn outcome=partial",
                                 number, TABLE_SCREEN_DPI, assessment["region_bbox"], type(exc).__name__,
                                 round((time.monotonic() - started) * 1000))
                elif assessment is not None:
                    table_extraction_diagnostics.update(status="not_screened_low_score",
                                                        candidate_screen="below_threshold_not_sampled")
                if len(pending_screen_pages) == 4:
                    _finish_vision_screen_batch(pending_screen_pages, self.table_extractor, fitz, document)
                    pending_screen_pages = []
            if pending_screen_pages:
                _finish_vision_screen_batch(pending_screen_pages, self.table_extractor, fitz, document)
        return pages


def _finish_vision_screen_batch(
        batch: list[tuple[Any, ...]],
        extractor: Any, fitz: Any, document: Any) -> None:
    page_numbers = [item[4] for item in batch]
    started = time.monotonic()
    contact_sheet = _vision_candidate_contact_sheet(batch, fitz)
    screen_failure_code = None
    try:
        candidates = set(extractor.detect_candidates(contact_sheet, page_numbers=page_numbers))
        screen_result = "completed"
    except Exception as exc:
        candidates = set()
        screen_result = "native_candidates_only"
        screen_failure_code = str(exc) if str(exc).startswith("TABLE_LLM_") else type(exc).__name__
        # shortcut: visual-only tables remain unresolved during a screening outage; rerun after the provider recovers.
        _dev_log("dev_preprocessing_table_llm stage=candidate_screen_failed expectedPageCount=%d pageNumbers=%s expectedOutcome=valid_candidate_page_numbers actualFailureType=%s actualFailureCode=%s elapsedMs=%d route=native_candidate_fallback recoveryAction=extract_native_candidates_and_mark_other_pages_unresolved outcome=warning",
                 len(batch), page_numbers, type(exc).__name__, screen_failure_code,
                 round((time.monotonic() - started) * 1000))
    native_candidates = {item[4] for item in batch if item[7]}
    selected = candidates | native_candidates
    _dev_log("dev_preprocessing_table_llm stage=candidate_screen_completed expectedPageCount=%d actualCandidateCount=%d actualNativeCandidateCount=%d pageNumbers=%s candidatePageNumbers=%s screenResult=%s imageBytes=%d elapsedMs=%d route=recall_first_union recoveryAction=extract_selected_pages outcome=completed",
             len(batch), len(candidates), len(native_candidates), page_numbers, sorted(selected), screen_result,
             len(contact_sheet), round((time.monotonic() - started) * 1000))
    for page_result, _, _, _, page_number, page_width, page_height, native_candidate, assessment in batch:
        if page_number not in selected:
            assessment = page_result.get("table_extraction_diagnostics", {}).get("candidate_assessment")
            page_result["table_extraction_diagnostics"] = {
                "status": "no_tables" if screen_result == "completed" else "candidate_screen_failed",
                "provider": "openai_compatible_vision",
                "model": getattr(extractor, "model", "injected"), "table_count": 0, "cell_count": 0,
                "candidate_screen": ("completed_no_candidate" if screen_result == "completed"
                                      else "failed_native_candidates_only"),
                **({"failure_code": screen_failure_code or "TABLE_LLM_CANDIDATE_SCREEN_FAILED"}
                   if screen_result != "completed" else {}),
                **({"candidate_assessment": assessment} if assessment is not None else {}),
            }
            _dev_log("dev_preprocessing_table_llm stage=candidate_region_skipped pageNumber=%d expectedScreenSuccess=true actualNativeCandidate=%s actualVisualCandidate=false actualScreenResult=%s recoveryAction=keep_pymupdf_text_and_mark_unresolved outcome=%s",
                     page_number, native_candidate, screen_result,
                     "skipped" if screen_result == "completed" else "warning")
            continue
        page_started = time.monotonic()
        candidate_reason = ("screen_failed_native_candidate" if screen_result != "completed" else
                            "native_and_visual" if native_candidate and page_number in candidates else
                            "native_geometry" if native_candidate else "visual_screen")
        try:
            source_page = document.load_page(page_number - 1)
            extraction_result = _extract_vision_candidate_region(
                page_result, source_page, assessment, page_number, page_width, page_height, extractor, fitz)
            page_result["table_extraction_diagnostics"]["candidate_screen"] = candidate_reason
            _dev_log("dev_preprocessing_table_llm stage=candidate_region_extraction_completed pageNumber=%d expectedCandidate=true actualRegionCount=%d actualTableCount=%d actualCellCount=%d imageBytes=%d elapsedMs=%d route=cropped_region_provider recoveryAction=preserve_unmatched_native_text outcome=%s",
                     page_number, len(assessment.get("extraction_regions", ())),
                     page_result["table_extraction_diagnostics"]["table_count"],
                     page_result["table_extraction_diagnostics"]["cell_count"], extraction_result["image_bytes"],
                     round((time.monotonic() - page_started) * 1000),
                     page_result["table_extraction_diagnostics"]["status"])
        except Exception as exc:
            assessment = page_result.get("table_extraction_diagnostics", {}).get("candidate_assessment")
            page_result["table_extraction_diagnostics"] = {
                "status": "failed", "provider": "openai_compatible_vision",
                "model": getattr(extractor, "model", "injected"), "table_count": 0, "cell_count": 0,
                "candidate_screen": candidate_reason,
                "failure_code": str(exc) if str(exc).startswith("TABLE_LLM_") else type(exc).__name__,
                **({"candidate_assessment": assessment} if assessment is not None else {}),
            }
            _dev_log("dev_preprocessing_table_llm stage=candidate_region_extraction_failed pageNumber=%d expectedCandidate=true actualFailureType=%s elapsedMs=%d route=pymupdf_text_only recoveryAction=preserve_native_text_and_warn outcome=partial",
                     page_number, type(exc).__name__, round((time.monotonic() - page_started) * 1000))


def _candidate_clip(assessment: Mapping[str, Any], page_width: float, page_height: float, fitz: Any) -> Any:
    raw = assessment.get("region_bbox")
    if not isinstance(raw, (list, tuple)) or len(raw) != 4:
        return fitz.Rect(0, 0, page_width, page_height)
    x0, y0, x1, y1 = (float(value) for value in raw)
    margin = max(8.0, min(24.0, max(x1 - x0, y1 - y0) * .015))
    return fitz.Rect(max(0.0, x0 - margin), max(0.0, y0 - margin),
                     min(page_width, x1 + margin), min(page_height, y1 + margin))


def _extract_vision_candidate_region(page_result: dict[str, Any], page: Any,
                                     assessment: Mapping[str, Any], page_number: int,
                                     page_width: float, page_height: float,
                                     extractor: Any, fitz: Any) -> dict[str, int]:
    started = time.monotonic()
    regions = assessment.get("extraction_regions") or []
    tables: list[dict[str, Any]] = []
    image_bytes = 0
    failed_regions = []
    if not regions:
        page_result["table_extraction_diagnostics"].update(
            status="candidate_region_unresolved", failure_code="TABLE_CANDIDATE_REGION_UNRESOLVED",
            candidate_region_bbox=assessment.get("region_bbox"), candidate_score=assessment.get("score"))
        _dev_log("dev_preprocessing_table_extract stage=region_unresolved pageNumber=%d expectedLocalRegion=true actualRegionCount=0 score=%s recoveryAction=preserve_native_text_and_warn outcome=warning",
                 page_number, assessment.get("score"))
        return {"image_bytes": 0}
    for index, region in enumerate(regions):
        if not isinstance(region, (list, tuple)) or len(region) != 4:
            continue
        region_assessment = {**assessment, "region_bbox": region}
        clip = _candidate_clip(region_assessment, page_width, page_height, fitz)
        try:
            rendered = page.get_pixmap(matrix=fitz.Matrix(1.5, 1.5), clip=clip, alpha=False).tobytes("png")
            local_tables = extractor.extract(rendered, page_width=clip.width, page_height=clip.height)
            tables.extend(_translate_crop_tables(local_tables, clip, page_width, page_height))
            image_bytes += len(rendered)
            region_area_ratio = clip.width * clip.height / max(page_width * page_height, 1)
            _dev_log("dev_preprocessing_table_extract stage=region_extracted pageNumber=%d regionIndex=%d expectedRegion=%s actualRegion=%s actualRegionAreaRatio=%.4f score=%s imageBytes=%d elapsedMs=%d route=cropped_region_provider recoveryAction=preserve_unmatched_native_text outcome=completed",
                     page_number, index, region,
                     [round(float(value), 2) for value in (clip.x0, clip.y0, clip.x1, clip.y1)],
                     region_area_ratio, assessment.get("score"), len(rendered),
                     round((time.monotonic() - started) * 1000))
        except Exception as exc:
            failure_code = str(exc) if str(exc).startswith("TABLE_LLM_") else type(exc).__name__
            failed_regions.append({"region_index": index, "failure_code": failure_code})
            _dev_log("dev_preprocessing_table_extract stage=region_failed pageNumber=%d regionIndex=%d expectedRegion=%s actualFailureType=%s actualFailureCode=%s elapsedMs=%d route=continue_other_regions recoveryAction=preserve_successful_regions_and_native_text outcome=partial",
                     page_number, index, region, type(exc).__name__, failure_code,
                     round((time.monotonic() - started) * 1000))
    _merge_vision_tables_into_page(page_result, tables, page_number, page_width, page_height, extractor)
    if failed_regions:
        page_result["table_extraction_diagnostics"]["region_extraction_failures"] = failed_regions
        page_result["table_extraction_diagnostics"]["status"] = (
            "partial_tables" if page_result["table_extraction_diagnostics"].get("table_count", 0) else "failed")
    page_result["table_extraction_diagnostics"].update(
        candidate_region_bbox=assessment.get("region_bbox"),
        candidate_score=assessment.get("score"), extraction_resolution_dpi=108)
    return {"image_bytes": image_bytes}


def _translate_crop_tables(tables: Sequence[Mapping[str, Any]], clip: Any,
                           page_width: float, page_height: float) -> list[dict[str, Any]]:
    translated: list[dict[str, Any]] = []
    for table in tables:
        result = dict(table)
        result["bbox"] = _crop_box_to_page(table.get("bbox"), clip, page_width, page_height)
        cells = table.get("cells")
        if not isinstance(cells, list):
            raise ValueError("TABLE_LLM_INVALID_CELLS")
        result["cells"] = [{**dict(cell), "bbox": _crop_box_to_page(cell.get("bbox"), clip, page_width, page_height)}
                           for cell in cells if isinstance(cell, Mapping)]
        if len(result["cells"]) != len(cells):
            raise ValueError("TABLE_LLM_INVALID_CELL")
        translated.append(result)
    return translated


def _crop_box_to_page(value: object, clip: Any, page_width: float, page_height: float) -> list[int]:
    if not isinstance(value, (list, tuple)) or len(value) != 4:
        raise ValueError("TABLE_LLM_INVALID_BBOX")
    x0, y0, x1, y1 = (float(item) for item in value)
    if not (0 <= x0 < x1 <= 1000 and 0 <= y0 < y1 <= 1000):
        raise ValueError("TABLE_LLM_INVALID_BBOX")
    return [max(0, min(1000, round((clip.x0 + x0 * clip.width / 1000) * 1000 / page_width))),
            max(0, min(1000, round((clip.y0 + y0 * clip.height / 1000) * 1000 / page_height))),
            max(0, min(1000, round((clip.x0 + x1 * clip.width / 1000) * 1000 / page_width))),
            max(0, min(1000, round((clip.y0 + y1 * clip.height / 1000) * 1000 / page_height)))]


def _merge_vision_tables_into_page(page_result: dict[str, Any], tables: list[dict[str, Any]],
                                   page_number: int, page_width: float, page_height: float,
                                   extractor: Any) -> None:
    from preprocessing_agent.adapters.llm_table_extractor import page_table_blocks

    candidate_diagnostics = page_result.get("table_extraction_diagnostics", {})
    preserved_assessment = candidate_diagnostics.get("candidate_assessment")
    preserved_region = candidate_diagnostics.get("candidate_region_bbox")
    preserved_score = candidate_diagnostics.get("candidate_score")
    preserved_resolution = candidate_diagnostics.get("extraction_resolution_dpi")
    table_blocks, table_regions, rejected_tables = page_table_blocks(
        tables, page_number=page_number, page_width=page_width, page_height=page_height)
    blocks, replaced_count, preserved_overlap_count = _merge_vision_table_blocks(
        page_result["blocks"], table_blocks, table_regions)
    if table_blocks:
        by_id = {str(block["block_id"]): block for block in blocks}
        plan = ReadingOrderPlanner().plan(blocks)
        blocks = [by_id[block_id] for block_id in (*plan.ordered_block_ids, *plan.furniture_block_ids)]
    page_result["blocks"] = blocks
    page_result["table_extraction_diagnostics"] = {
        "status": ("partial_tables" if table_blocks and rejected_tables else
                   "tables_found" if table_blocks else "failed" if rejected_tables else "no_tables"),
        "provider": "openai_compatible_vision", "model": getattr(extractor, "model", "injected"),
        "table_count": len(table_regions), "cell_count": len(table_blocks),
        "native_blocks_replaced": replaced_count,
        "overlapping_native_blocks_preserved": preserved_overlap_count,
        "rejected_tables": rejected_tables,
        **({"candidate_assessment": preserved_assessment} if preserved_assessment is not None else {}),
        **({"candidate_region_bbox": preserved_region} if preserved_region is not None else {}),
        **({"candidate_score": preserved_score} if preserved_score is not None else {}),
        **({"extraction_resolution_dpi": preserved_resolution} if preserved_resolution is not None else {}),
    }
    _dev_log("dev_preprocessing_table_llm stage=page_merge_completed pageNumber=%d expectedTableCount=%d actualTableCount=%d actualCellCount=%d replacedNativeBlockCount=%d preservedOverlapBlockCount=%d rejectedCellCount=%d outcome=%s",
             page_number, len(tables), len(table_regions), len(table_blocks), replaced_count,
             preserved_overlap_count, len(rejected_tables), page_result["table_extraction_diagnostics"]["status"])
    for rejected in rejected_tables:
        if rejected["failure_code"] == "TABLE_LLM_DUPLICATE_OVERLAPPING_REGION":
            _dev_log("dev_preprocessing_table_llm stage=duplicate_table_discarded pageNumber=%d tableIndex=%d duplicateOfTableIndex=%d expectedSameCellContentAndOverlap=true actualFailureCode=%s recoveryAction=keep_first_table outcome=deduplicated",
                     page_number, rejected["table_index"], rejected["duplicate_of_table_index"],
                     rejected["failure_code"])
            continue
        _dev_log("dev_preprocessing_table_llm stage=table_cell_rejected pageNumber=%d tableIndex=%d cellIndex=%d row=%d column=%d expectedFailureCode=TABLE_LLM_CELL_OUTSIDE_TABLE actualFailureCode=%s expectedCellInsideTable=true actualTableBbox=%s actualCellBbox=%s recoveryAction=preserve_native_text outcome=rejected",
                 page_number, rejected["table_index"], rejected["cell_index"], rejected["row"],
                 rejected["column"], rejected["failure_code"], rejected["table_bbox"], rejected["cell_bbox"])


def _vision_candidate_contact_sheet(
        pages: list[tuple[dict[str, Any], bytes, int, int, int, float, float, bool]], fitz: Any) -> bytes:
    columns, label_height = 4, 20
    tile_width = max(item[2] for item in pages)
    tile_height = max(item[3] for item in pages) + label_height
    sheet = fitz.open()
    try:
        page = sheet.new_page(width=columns * tile_width, height=math.ceil(len(pages) / columns) * tile_height)
        for slot, (_, image, width, height, number, *_rest) in enumerate(pages):
            left, top = slot % columns * tile_width, slot // columns * tile_height
            page.insert_text((left + 4, top + 14), f"PDF page {number}", fontsize=10)
            page.insert_image(fitz.Rect(left, top + label_height, left + width, top + label_height + height), stream=image)
        return page.get_pixmap(alpha=False).tobytes("png")
    finally:
        sheet.close()


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


def _layout_review_payload(page: Mapping[str, Any]) -> dict[str, Any]:
    layout = page.get("layout") if isinstance(page.get("layout"), Mapping) else {}
    return {**layout, "blocks": page.get("blocks", []),
            "table_diagnostics": page.get("table_diagnostics", [])}


class ExtractionApplicationService:
    def __init__(self, native_pdf: NativePdfPort | None = None, render: PageRenderPort | None = None,
                 ocr: OcrPort | None = None, *, analyze_structure: bool = False) -> None:
        self.analyze_structure = analyze_structure
        if native_pdf is not None:
            self.native_pdf = native_pdf
        else:
            provider = os.getenv("PREPROCESSING_PDF_PROVIDER", "hybrid").strip().casefold()
            if provider == "hybrid":
                from preprocessing_agent.adapters.hybrid_pdf import HybridPdfAdapter
                self.native_pdf = HybridPdfAdapter()
            elif provider == "pymupdf4llm":
                from preprocessing_agent.adapters.pymupdf4llm_pdf import PyMuPdf4LlmPdfAdapter
                self.native_pdf = PyMuPdf4LlmPdfAdapter()
            elif provider == "pymupdf":
                self.native_pdf = PyMuPdfNativePdfAdapter(analyze_structure=analyze_structure)
            elif provider == "docling":
                from preprocessing_agent.adapters.docling_pdf import DoclingPdfAdapter
                self.native_pdf = DoclingPdfAdapter()
            else:
                raise ValueError(f"unsupported PREPROCESSING_PDF_PROVIDER: {provider}")
        self._pdf_provider_name = getattr(self.native_pdf, "provider_name", "injected")
        _dev_log("dev_preprocessing_pdf_provider stage=selected expectedProvider=hybrid actualProvider=%s route=application_extraction outcome=selected",
                 self._pdf_provider_name)
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
        started = time.monotonic()
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
        extraction_started = time.monotonic()
        _dev_log("dev_preprocessing_run stage=source_extraction_started provider=%s expectedPageCount=from_source actualRecoveredPageCount=%d sourceType=%s outcome=started",
                 self._pdf_provider_name, len(recovered) if isinstance(recovered, Mapping) else 0, source.suffix.lower() or "text")
        try:
            raw_pages = self.native_pdf.extract(source) if source.suffix.lower() == ".pdf" else self._text_pages(source)
            if isinstance(recovered, Mapping):
                raw_pages = [recovered.get(str(p.get("page_number")), p) if isinstance(p, Mapping) else p for p in raw_pages]
            if not isinstance(raw_pages, (list, tuple)):
                raw_pages = [{"page_number": 1, "malformed": True}]
        except Exception as exc:
            if isinstance(recovered, Mapping) and recovered:
                raw_pages = list(recovered.values())
                _dev_log("dev_preprocessing_run stage=source_extraction_fallback expectedPageCount=from_source actualRecoveredPageCount=%d failureType=%s route=recovered_page_artifacts recoveryAction=continue_page_processing elapsedMs=%d outcome=fallback",
                         len(raw_pages), type(exc).__name__,
                         round((time.monotonic() - extraction_started) * 1000))
            else:
                _dev_log("dev_preprocessing_run stage=source_extraction_failed expectedPageCount=from_source actualRecoveredPageCount=0 failureType=%s route=none recoveryAction=preserve_source_require_reextraction elapsedMs=%d outcome=failed",
                         type(exc).__name__, round((time.monotonic() - extraction_started) * 1000))
                raise ValueError("NATIVE_EXTRACTION_FAILED") from exc
        document_id = source_hash[:16]
        version_id = str(request.get("version_id") or f"ev-{uuid.uuid4().hex[:12]}")
        if not re.fullmatch(r"[A-Za-z0-9._-]+", version_id) or version_id in {".", ".."}:
            raise ValueError("INVALID_REQUEST")
        version = ExtractionVersion.create(version_id, document_id, policy, len(raw_pages) or 1)
        _dev_log("dev_preprocessing_run stage=source_extraction_completed expectedPageCount=from_source actualPageCount=%d elapsedMs=%d route=page_provenance_validation outcome=success",
                 version.page_count, round((time.monotonic() - extraction_started) * 1000))
        reference_font_size = document_reference_font_size(raw_pages) if self.analyze_structure else None
        _dev_log("dev_preprocessing_run stage=page_scan_started expectedPageCount=%d actualExtractedPageCount=%d route=%s outcome=started",
                 version.page_count, len(raw_pages),
                 "development_structure_analysis" if self.analyze_structure else "text_provenance_form_classification")
        parser_pages_by_number: dict[int, Mapping[str, Any]] = {}
        page_artifacts: list[dict[str, Any]] = []
        seen_page_numbers: set[int] = set()
        for position, raw in enumerate(raw_pages, 1):
            page_started = time.monotonic()
            number: int | None = None
            safe_search_page: Mapping[str, Any] | None = None
            heading_evidence: list[Any] = []
            heading_candidate_evidence: list[Any] = []
            table_evidence: list[Any] = []
            render_evidence: dict[str, Any] = {"page_number": position, "status": "UNAVAILABLE"}
            layout_validation: Mapping[str, Any] = {}
            layout_plan = None
            table_diagnostics: list[Mapping[str, Any]] = []
            pending_findings: list[str] = []
            _dev_log("dev_preprocessing_page stage=started pageNumber=%d expectedPageCount=%d outcome=started",
                     position, version.page_count)
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
                    pending_findings.append(str(raw["capability_error"]))
                    if not any(str(block.get("text", "")).strip() for block in raw.get("blocks", ())):
                        raise ValueError(str(raw["capability_error"]))
                table_diagnostic = raw.get("table_extraction_diagnostics", {})
                if isinstance(table_diagnostic, Mapping) and table_diagnostic.get("status") in {"failed", "partial_tables", "not_configured"}:
                    pending_findings.append("TABLE_EXTRACTION_NOT_CONFIGURED" if table_diagnostic.get("status") == "not_configured"
                                            else "TABLE_EXTRACTION_PARTIAL" if table_diagnostic.get("status") == "partial_tables"
                                            else "TABLE_EXTRACTION_FAILED")
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
                has_text = any(str(block.get("text", "")).strip() for block in blocks)
                form_state = str(raw.get("form_content_state", "blank"))
                if has_text:
                    # Page and bounding-box provenance is assigned above. Structural
                    # review findings must not discard otherwise usable source text.
                    safe_search_page = raw
                    parser_pages_by_number[number] = raw
                is_form = raw.get("page_classification") == "form"
                form_diagnostics = raw.get("form_diagnostics")
                expected_form_fields = {"drawing_count", "aligned_line_count", "long_horizontal_line_count", "text_line_count"}
                verified_form = bool(
                    is_form and isinstance(form_diagnostics, Mapping)
                    and expected_form_fields <= form_diagnostics.keys()
                    and all(type(form_diagnostics[key]) is int and form_diagnostics[key] >= 0
                            for key in expected_form_fields)
                    and is_form_like(**{key: form_diagnostics[key] for key in expected_form_fields})
                )
                if is_form and not verified_form:
                    pending_findings.append("FORM_CLASSIFICATION_UNVERIFIED")
                    _dev_log("dev_preprocessing_form stage=classification pageNumber=%d expectedEvidenceFields=4 actualEvidenceFields=%d route=needs_review outcome=unverified",
                             number, len(form_diagnostics.keys()) if isinstance(form_diagnostics, Mapping) else 0)
                elif verified_form and form_state == "blank":
                    evidence = {"page_number": number, "page_classification": "form",
                                "form_content_state": form_state,
                                "form_diagnostics": {key: form_diagnostics[key] for key in sorted(expected_form_fields)},
                                "exclusion_evidence": {"classifier": "form_geometry", "decision": "EXCLUDED_FORM"},
                                "geometry": {"width": geometry.width, "height": geometry.height,
                                             "unit": geometry.unit, "origin": geometry.origin},
                                "blocks": blocks}
                    version.record_page(PageExtraction(number, PageStatus.EXCLUDED_FORM))
                    parser_pages_by_number[number] = raw
                    page_artifacts.append({**evidence, "status": PageStatus.EXCLUDED_FORM.value,
                                           "attempts": raw.get("_retry_attempts", 1),
                                           "attempt_history": raw.get("_retry_attempt_history", [{
                                               "attempt": 1, "status": PageStatus.EXCLUDED_FORM.value,
                                               "findings": []}]),
                                           "evidence_sha256": hashlib.sha256(
                                               json.dumps(evidence, sort_keys=True).encode()).hexdigest()})
                    _dev_log("dev_preprocessing_form stage=excluded pageNumber=%d drawingCount=%d alignedLineCount=%d longHorizontalLineCount=%d textLineCount=%d expectedPageCount=%d actualExcludedFormCount=%d recoveryAction=preserve_source_skip_parser outcome=excluded",
                             number, form_diagnostics["drawing_count"], form_diagnostics["aligned_line_count"],
                             form_diagnostics["long_horizontal_line_count"], form_diagnostics["text_line_count"],
                             version.page_count,
                             sum(item.status == PageStatus.EXCLUDED_FORM for item in version.pages.values()))
                    _dev_log("dev_preprocessing_page stage=completed pageNumber=%d expectedStatus=EXCLUDED_FORM actualStatus=%s elapsedMs=%d route=preserve_form_asset outcome=excluded",
                             number, PageStatus.EXCLUDED_FORM.value,
                             round((time.monotonic() - page_started) * 1000))
                    continue
                elif verified_form and form_state == "unknown":
                    pending_findings.append("FORM_CONTENT_UNVERIFIED")
                    _dev_log("dev_preprocessing_form stage=content_assessment pageNumber=%d expectedState=blank_or_filled actualState=unknown populatedFieldCount=%d imageRegionCount=%d route=%s recoveryAction=do_not_exclude_as_blank outcome=review_required",
                             number, int(raw.get("populated_form_field_count", 0)),
                             len(raw.get("image_regions", ())),
                             "ocr_and_preserve_page_asset" if raw.get("image_regions") else "preserve_text_and_page_asset")
                elif verified_form and form_state == "filled":
                    pending_findings.append("FORM_VALUES_PRESERVED")
                    _dev_log("dev_preprocessing_form stage=content_assessment pageNumber=%d expectedPopulatedFieldCount=0 actualPopulatedFieldCount=%d route=searchable_form_content recoveryAction=preserve_field_values_and_source_positions outcome=usable_with_warning",
                             number, int(raw.get("populated_form_field_count", 0)))
                if not has_text:
                    raise ValueError("NO_USABLE_TEXT")
                if self.analyze_structure:
                    table_diagnostics = [item for item in raw.get("native_table_diagnostics", ())
                                         if isinstance(item, Mapping)]
                    if any(item.get("decision") == "strong_unresolved" for item in table_diagnostics):
                        pending_findings.append("TABLE_STRUCTURE_UNRESOLVED")
                    cached_approval = None
                    if not isinstance(raw.get("layout_confirmation"), Mapping):
                        cached_approval = _load_layout_approval(output, source_hash, number, raw)
                        _dev_log("dev_preprocessing_layout_approval stage=cache_lookup pageNumber=%d outcome=%s",
                                 number, "hit" if cached_approval else "miss")
                    if cached_approval:
                        raw["layout_confirmation"] = {**cached_approval, "_approval_cache_hit": True}
                    confirmation = raw.get("layout_confirmation") if isinstance(raw.get("layout_confirmation"), Mapping) else {}
                    confirmed_columns = bool(
                        isinstance(confirmation.get("admin_id"), str) and confirmation.get("admin_id")
                        and isinstance(confirmation.get("candidate_version"), str)
                        and (confirmation.get("candidate_version") == request.get("retry_source_version_id")
                             or confirmation.get("_approval_cache_hit") is True)
                        and isinstance(confirmation.get("selections"), Mapping)
                        and confirmation.get("selections")
                    )
                    layout_plan = (ReadingOrderPlanner().form_plan(blocks, geometry) if is_form else
                                   ReadingOrderPlanner().plan(blocks, geometry,
                                       dict(confirmation.get("selections", {})) if confirmed_columns else None))
                    layout_evidence = to_dict(layout_plan)
                    raw["layout"] = layout_evidence
                    heading_associations, heading_candidates = HeadingAssociator().analyze(
                        blocks, layout_plan, reference_font_size)
                    heading_evidence = to_dict(heading_associations)
                    heading_candidate_evidence = to_dict(heading_candidates)
                    table_values = TableStructureDetector().detect(blocks)
                    table_evidence = to_dict(table_values)
                    table_findings = [finding for table in table_values for finding in table.findings]
                    if table_findings:
                        raise ValueError("IRREGULAR_TABLE: " + ",".join(sorted(set(table_findings))))
                    raw.update(heading_associations=heading_evidence,
                               heading_candidates=heading_candidate_evidence, tables=table_evidence)
                    render_evidence = self._render_evidence(source, number, geometry)
                    validation = self.layout_validator.validate(raw, render_evidence,
                        confirmed_columns=confirmed_columns)
                    layout_validation = validation.as_dict()
                    raw["layout_validation"] = layout_validation
                    if not validation.valid:
                        raise ValueError("LAYOUT_VALIDATION_FAILED: " + ",".join(
                            item.code for item in validation.findings))
                    if pending_findings:
                        raise ValueError(",".join(pending_findings))
                    evidence = {"page_number": number,
                                "page_classification": raw.get("page_classification", "text-native"),
                                "form_content_state": raw.get("form_content_state"),
                                "image_regions": raw.get("image_regions", []),
                                "form_diagnostics": form_diagnostics or {},
                                "geometry": {"width": geometry.width, "height": geometry.height,
                                             "unit": geometry.unit, "origin": geometry.origin},
                                "blocks": blocks, "layout": layout_evidence,
                                "heading_associations": heading_evidence,
                                "heading_candidates": heading_candidate_evidence,
                                "table_extraction_diagnostics": raw.get("table_extraction_diagnostics", {}),
                                "tables": table_evidence, "layout_validation": layout_validation}
                    page_status = PageStatus.VALIDATED
                else:
                    evidence = {"page_number": number,
                                "page_classification": raw.get("page_classification", "text-native"),
                                "form_content_state": raw.get("form_content_state"),
                                "image_regions": raw.get("image_regions", []),
                                "form_diagnostics": form_diagnostics or {},
                                "geometry": {"width": geometry.width, "height": geometry.height,
                                             "unit": geometry.unit, "origin": geometry.origin},
                                "blocks": blocks,
                                "table_extraction_diagnostics": raw.get("table_extraction_diagnostics", {})}
                page_status = (PageStatus.USABLE_WITH_WARNINGS if pending_findings
                               else page_status if self.analyze_structure else PageStatus.VALIDATED)
                findings = list(dict.fromkeys(pending_findings))
                version.record_page(PageExtraction(number, page_status, findings))
                evidence["findings"] = findings
                page_artifacts.append({**evidence, "status": page_status.value,
                    "attempts": raw.get("_retry_attempts", 1),
                    "attempt_history": raw.get("_retry_attempt_history", [{"attempt": 1,
                        "status": page_status.value, "findings": findings}]),
                    "layout_review": raw.get("layout_review") if self.analyze_structure else None,
                    "layout_confirmation": raw.get("layout_confirmation") if self.analyze_structure else None,
                    "table_diagnostics": table_diagnostics if self.analyze_structure else [],
                    "evidence_sha256": hashlib.sha256(json.dumps(evidence, sort_keys=True).encode()).hexdigest()})
                _dev_log("dev_preprocessing_page stage=completed pageNumber=%d expectedTextBlockCount=at_least_1 actualTextBlockCount=%d actualTextCharacters=%d actualStatus=%s findingCount=%d elapsedMs=%d route=source_text_and_provenance outcome=%s",
                         number, sum(bool(str(block.get("text", "")).strip()) for block in blocks),
                         sum(len(str(block.get("text", ""))) for block in blocks), page_status.value,
                         len(findings), round((time.monotonic() - page_started) * 1000),
                         "warning" if findings else "success")
            except Exception as exc:
                safe_number = number if "number" in locals() and 1 <= number <= version.page_count else position
                finding = str(exc) or "INVALID_GEOMETRY"
                findings = list(dict.fromkeys([*pending_findings,
                    *(item.strip() for item in finding.replace(":", ",").split(",") if item.strip())]))
                page_status = (PageStatus.USABLE_WITH_WARNINGS if safe_search_page is not None
                               else PageStatus.NEEDS_REVIEW)
                version.record_page(PageExtraction(safe_number, page_status, findings))
                page_artifacts[:] = [item for item in page_artifacts if item.get("page_number") != safe_number]
                evidence = {"page_number": safe_number, "status": PageStatus.NEEDS_REVIEW.value,
                            "findings": version.pages[safe_number].findings,
                            "page_classification": raw.get("page_classification", "text-native") if isinstance(raw, Mapping) else "text-native",
                            "form_diagnostics": raw.get("form_diagnostics", {}) if isinstance(raw, Mapping) else {},
                            "form_content_state": raw.get("form_content_state", "unknown") if isinstance(raw, Mapping) else "unknown",
                            "image_regions": raw.get("image_regions", []) if isinstance(raw, Mapping) else [],
                            "blocks": raw.get("blocks", []) if isinstance(raw, Mapping) else [],
                            "table_extraction_diagnostics": raw.get("table_extraction_diagnostics", {}) if isinstance(raw, Mapping) else {},
                            "geometry": raw.get("geometry", {}) if isinstance(raw, Mapping) else {},
                            "layout": to_dict(layout_plan) if "layout_plan" in locals() and layout_plan is not None else {},
                            "table_diagnostics": table_diagnostics,
                            "heading_associations": heading_evidence, "heading_candidates": heading_candidate_evidence, "tables": table_evidence,
                            "render_evidence": render_evidence, "layout_validation": layout_validation}
                page_artifacts.append({**evidence, "status": page_status.value,
                                       "attempts": raw.get("_retry_attempts", 1) if isinstance(raw, Mapping) else 1,
                                       "attempt_history": raw.get("_retry_attempt_history", [{
                                           "attempt": 1, "status": page_status.value, "findings": findings}]) if isinstance(raw, Mapping) else [{
                                           "attempt": 1, "status": page_status.value, "findings": findings}],
                                       "evidence_sha256": hashlib.sha256(json.dumps(evidence, sort_keys=True).encode()).hexdigest()})
                _dev_log("dev_preprocessing_page stage=completed pageNumber=%d expectedUsableText=source_text_with_page_provenance actualTextBlockCount=%d actualTextCharacters=%d expectedStatus=VALIDATED actualStatus=%s findingCount=%d failureType=%s elapsedMs=%d route=%s recoveryAction=%s outcome=%s",
                         safe_number,
                         sum(bool(str(block.get("text", "")).strip()) for block in safe_search_page.get("blocks", ())) if safe_search_page else 0,
                         sum(len(str(block.get("text", ""))) for block in safe_search_page.get("blocks", ())) if safe_search_page else 0,
                         page_status.value, len(findings), type(exc).__name__,
                         round((time.monotonic() - page_started) * 1000),
                         "continue_with_searchable_page" if safe_search_page else "quarantine_page",
                         "publish_partial_artifacts" if safe_search_page else "preserve_source_and_require_reextraction",
                         "warning" if safe_search_page else "needs_review")
        present_pages = {item["page_number"] for item in page_artifacts}
        for missing in range(1, version.page_count + 1):
            if missing not in present_pages:
                evidence = {"page_number": missing, "status": PageStatus.NEEDS_REVIEW.value, "findings": ["MISSING_PAGE_METADATA"]}
                page_artifacts.append({**evidence, "evidence_sha256": hashlib.sha256(json.dumps(evidence, sort_keys=True).encode()).hexdigest()})
                _dev_log("dev_preprocessing_page stage=completed pageNumber=%d expectedPageMetadata=true actualPageMetadata=false actualStatus=NEEDS_REVIEW failureType=MISSING_PAGE_METADATA elapsedMs=0 route=quarantine_page recoveryAction=preserve_source outcome=needs_review",
                         missing)
        page_artifacts.sort(key=lambda item: item["page_number"])
        published = False
        manifest: Mapping[str, Any] = {}
        output.mkdir(parents=True, exist_ok=True)
        versions = output / "versions"
        _canonical_path(str(versions))
        versions.mkdir(exist_ok=True)
        temp_dir = Path(tempfile.mkdtemp(prefix=f".{version_id}-", dir=str(versions)))
        try:
            searchable_page_count = sum(page.status in {PageStatus.VALIDATED, PageStatus.USABLE_WITH_WARNINGS}
                                        for page in version.pages.values())
            can_materialize = (searchable_page_count > 0 and len(version.pages) == version.page_count)
            forms_only = (len(version.pages) == version.page_count and version.pages
                          and all(page.status == PageStatus.EXCLUDED_FORM for page in version.pages.values()))
            if can_materialize or forms_only:
                version.publish()
                parser_pages = [parser_pages_by_number.get(number, {"page_number": number, "blocks": []})
                                for number in range(1, version.page_count + 1)]
                parser = PdfDocumentParser(extractor=lambda _source: parser_pages, analyze_structure=False)
                result = PreprocessingPipeline.from_config({"name": "extraction-version", "pipeline_version": policy}, parser=parser, output_dir=temp_dir).run(source=source)
                manifest = dict(result.manifest)
                manifest_path = temp_dir / "manifest.json"
                manifest_data = json.loads(manifest_path.read_text())
                manifest_data["source_sha256"] = source_hash
                if self._pdf_provider_name in {"pymupdf", "pymupdf4llm", "docling", "hybrid"}:
                    manifest_data["pdf_provider"] = self._pdf_provider_name
                manifest_data.setdefault("source", {})["sha256"] = source_hash
                excluded_form_pages = sorted(number for number, page in version.pages.items()
                                             if page.status == PageStatus.EXCLUDED_FORM)
                manifest_data.setdefault("statistics", {})["pages"] = version.page_count
                manifest_data["page_statistics"] = {"count": version.page_count,
                                                     "excluded_form_pages": excluded_form_pages}
                manifest_path.write_text(json.dumps(manifest_data, ensure_ascii=False, sort_keys=True, indent=2) + "\n")
                manifest = manifest_data
                published = True
            else:
                version.status = ExtractionStatus.NEEDS_REVIEW
                excluded_form_pages = sorted(number for number, page in version.pages.items()
                                             if page.status == PageStatus.EXCLUDED_FORM)
                manifest = {"source": {"path": str(source), "sha256": source_hash}, "source_sha256": source_hash,
                            "pipeline_version": policy, "schema_version": "1", "profile": "extraction-version",
                            "policy": {"version": policy}, "statistics": {"pages": version.page_count},
                            "page_statistics": {"count": version.page_count,
                                                "excluded_form_pages": excluded_form_pages}}
                (temp_dir / "manifest.json").write_text(json.dumps(manifest, sort_keys=True) + "\n")
            excluded_form_pages = sorted(number for number, page in version.pages.items()
                                         if page.status == PageStatus.EXCLUDED_FORM)
            version_artifact = {"version_id": version.version_id, "document_id": document_id,
                                "policy_version": policy, "page_count": version.page_count,
                                "excluded_form_pages": excluded_form_pages,
                                "status": version.status.value, "source_sha256": source_hash,
                                "pages": page_artifacts}
            (temp_dir / "version.json").write_text(json.dumps(version_artifact, ensure_ascii=False, sort_keys=True, indent=2) + "\n")
            response = {"schema_version": "1", "operation": "preprocess", "request_id": request_id,
                        "version_id": version.version_id, "status": version.status.value,
                        "pages": [{"page_number": item["page_number"], "status": item["status"],
                                   "attempts": item.get("attempts", 1), "findings": item.get("findings", []),
                                   "page_classification": item.get("page_classification"),
                                   "form_diagnostics": item.get("form_diagnostics"),
                                   "form_content_state": item.get("form_content_state"),
                                   "layout_review": item.get("layout_review") or (_layout_review_payload(item) if item["status"] in {"NEEDS_REVIEW", "USABLE_WITH_WARNINGS"} else None),
                                   "layout_confirmation": item.get("layout_confirmation"),
                                   "attempt_history": item.get("attempt_history", [{"attempt": 1,
                                       "status": item["status"], "findings": item.get("findings", [])}])}
                                  for item in page_artifacts],
                        "page_summary": {"count": len(page_artifacts), "processed": len(page_artifacts),
                                         "validated": sum(item["status"] == "VALIDATED" for item in page_artifacts),
                                         "usable_with_warnings": sum(item["status"] == "USABLE_WITH_WARNINGS" for item in page_artifacts),
                                         "excluded_forms": sum(item["status"] == "EXCLUDED_FORM" for item in page_artifacts),
                                         "needs_review": sum(item["status"] == "NEEDS_REVIEW" for item in page_artifacts),
                                         "ready": sum(item["status"] in {"VALIDATED", "USABLE_WITH_WARNINGS", "EXCLUDED_FORM"} for item in page_artifacts)},
                        "artifacts": self._artifact_refs(temp_dir, published), "manifest": manifest}
            (temp_dir / "response.json").write_text(json.dumps(response, ensure_ascii=False, sort_keys=True, indent=2) + "\n")
            version_dir = versions / version_id
            if version_dir.exists():
                raise ValueError("VERSION_ID_CONFLICT")
            temp_dir.rename(version_dir)
            if published:
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
            response = {**response, "artifacts": self._artifact_refs(version_dir, published)}
            if published and 'generation' in locals():
                response = {**response, "artifacts": self._artifact_refs(generation, published)}
            (version_dir / "response.json").write_text(json.dumps(response, ensure_ascii=False, sort_keys=True, indent=2) + "\n")
            if published and 'generation' in locals():
                shutil.copy2(version_dir / "response.json", generation / "response.json")
                pointer = {"version_id": version_id, "generation": str(generation), "status": version.status.value}
                (output / "current.json.tmp").write_text(json.dumps(pointer, sort_keys=True) + "\n")
                (output / "current.json.tmp").replace(output / "current.json")
            else:
                # Keep the prior current generation visible; this attempt is version-scoped.
                pass
            _dev_log("dev_preprocessing_run stage=completed status=%s expectedPageCount=%d actualPageCount=%d searchablePageCount=%d warningPageCount=%d reviewPageCount=%d chunkCount=%d elapsedMs=%d route=%s outcome=%s",
                     version.status.value, version.page_count, len(page_artifacts),
                     response["page_summary"]["ready"] - response["page_summary"]["excluded_forms"],
                     response["page_summary"]["usable_with_warnings"], response["page_summary"]["needs_review"],
                     manifest.get("statistics", {}).get("chunks", {}).get("exported", 0),
                     round((time.monotonic() - started) * 1000),
                     "publish_searchable_pages" if published else "preserve_review_only",
                     "success" if published else "needs_review")
            index[idempotency_key] = version_id
            index_tmp = index_path.with_suffix(".tmp")
            index_tmp.write_text(json.dumps(index, sort_keys=True, indent=2) + "\n")
            os.replace(index_tmp, index_path)
            return response
        except Exception as exc:
            _dev_log("dev_preprocessing_run stage=artifact_materialization_failed expectedPageCount=%d actualPageArtifactCount=%d searchablePageCount=%d failureType=%s failure=%s elapsedMs=%d recoveryAction=preserve_source_and_page_diagnostics outcome=failed",
                     version.page_count, len(page_artifacts),
                     sum(page.status in {PageStatus.VALIDATED, PageStatus.USABLE_WITH_WARNINGS}
                         for page in version.pages.values()), type(exc).__name__, str(exc),
                     round((time.monotonic() - started) * 1000))
            if temp_dir.exists(): shutil.rmtree(temp_dir)
            raise

    def _augment_with_ocr(self, source: Path, raw: Mapping[str, Any], number: int) -> Mapping[str, Any]:
        """Classify a page and OCR only declared image regions or image-only pages."""
        if raw.get("page_classification") == "form":
            if raw.get("form_content_state") == "blank" or not raw.get("image_regions"):
                return raw
        native_blocks = [dict(item) for item in raw.get("blocks", ()) if str(item.get("text", "")).strip()]
        image_regions = raw.get("image_regions", ())
        is_form = raw.get("page_classification") == "form"
        classification = "form" if is_form else ("text-native" if native_blocks and not image_regions else ("mixed" if native_blocks else "image-only"))
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
                if response["schema_version"] != "1" or response["operation"] not in {"preprocess", "status", "retry_pages"} or not isinstance(response["request_id"], str) or not response["request_id"] or response["version_id"] != version_id or response["status"] not in {"QUEUED", "PROCESSING", "VALIDATING", "READY", "PARTIAL", "NEEDS_REVIEW"}:
                    raise ValueError("VERSION_ARTIFACT_CORRUPT")
                summary = response.get("page_summary", {})
                if not isinstance(summary, dict) or any(type(summary.get(key)) is not int for key in ("count", "processed", "validated", "usable_with_warnings", "excluded_forms", "needs_review", "ready")) or summary.get("count") != len(response["pages"]) or summary.get("processed") != len(response["pages"]):
                    raise ValueError("VERSION_ARTIFACT_CORRUPT")
                statuses = [page.get("status") for page in response["pages"] if isinstance(page, dict)]
                if (summary.get("validated") != statuses.count("VALIDATED")
                        or summary.get("usable_with_warnings") != statuses.count("USABLE_WITH_WARNINGS")
                        or summary.get("excluded_forms") != statuses.count("EXCLUDED_FORM")
                        or summary.get("needs_review") != statuses.count("NEEDS_REVIEW")
                        or summary.get("ready") != statuses.count("VALIDATED") + statuses.count("USABLE_WITH_WARNINGS") + statuses.count("EXCLUDED_FORM")):
                    raise ValueError("VERSION_ARTIFACT_CORRUPT")
                if response["status"] == "READY" and any(page.get("status") in {"USABLE_WITH_WARNINGS", "NEEDS_REVIEW"} for page in response["pages"]):
                    raise ValueError("VERSION_ARTIFACT_CORRUPT")
                page_numbers = [page.get("page_number") if isinstance(page, dict) else None for page in response["pages"]]
                if len(set(page_numbers)) != len(page_numbers) or any(type(number) is not int or number < 1 for number in page_numbers) or page_numbers != sorted(page_numbers) or page_numbers != list(range(1, len(page_numbers) + 1)):
                    raise ValueError("VERSION_ARTIFACT_CORRUPT")
                for page in response["pages"]:
                    if not isinstance(page, dict) or type(page.get("page_number")) is not int or page.get("status") not in {"VALIDATED", "USABLE_WITH_WARNINGS", "EXCLUDED_FORM", "NEEDS_REVIEW"} or type(page.get("attempts")) is not int or page.get("attempts") < 1 or not isinstance(page.get("findings"), list) or any(not isinstance(item, str) for item in page.get("findings", [])):
                        raise ValueError("VERSION_ARTIFACT_CORRUPT")
                    if page.get("status") == "EXCLUDED_FORM":
                        diagnostics = page.get("form_diagnostics")
                        expected = {"drawing_count", "aligned_line_count", "long_horizontal_line_count", "text_line_count"}
                        if (page.get("page_classification") != "form" or not isinstance(diagnostics, Mapping)
                                or expected - diagnostics.keys()
                                or any(type(diagnostics[key]) is not int or diagnostics[key] < 0 for key in expected)
                                or not is_form_like(**{key: diagnostics[key] for key in expected})):
                            raise ValueError("VERSION_ARTIFACT_CORRUPT")
                if not isinstance(response["artifacts"].get("manifest_sha256"), (str, type(None))):
                    raise ValueError("VERSION_ARTIFACT_CORRUPT")
                manifest_ref = response["artifacts"].get("manifest")
                if not isinstance(manifest_ref, dict) or not re.fullmatch(r"[0-9a-f]{64}", str(response["artifacts"].get("manifest_sha256"))) or response["artifacts"].get("manifest_sha256") != manifest_ref.get("sha256"):
                    raise ValueError("VERSION_ARTIFACT_CORRUPT")
                allowed_refs = {"manifest_sha256", "manifest", "version", "chunks", "document_tree", "issues"}
                if set(response["artifacts"]) - allowed_refs or not {"manifest_sha256", "manifest", "version"} <= set(response["artifacts"]):
                    raise ValueError("VERSION_ARTIFACT_CORRUPT")
                if response["status"] in {"READY", "PARTIAL"} and not {"chunks", "document_tree", "issues"} <= set(response["artifacts"]):
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
                    excluded_form_pages = [page["page_number"] for page in response["pages"]
                                           if page.get("status") == "EXCLUDED_FORM"]
                    page_stats = manifest_data.get("page_statistics", {})
                    if (type(expected_pages) is not int or expected_pages != len(response["pages"])
                            or response["page_summary"].get("count") != expected_pages
                            or version_data.get("excluded_form_pages") != excluded_form_pages
                            or manifest_data.get("statistics", {}).get("pages") != expected_pages
                            or page_stats.get("count") != expected_pages
                            or page_stats.get("excluded_form_pages") != excluded_form_pages):
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

    def retry_pages(self, version_id: str, artifact_root: str | Path, pages: list[int], *, request_id: str = "retry",
                    layout_selections: dict[int, dict[str, int]] | None = None,
                    confirmed_by: str | None = None) -> Mapping[str, Any]:
        """Re-extract, render and validate only selected review pages.

        The version remains quarantined until every page is validated.  A
        retry is persisted through a temp file replacement so an interrupted
        caller can safely resume from the last complete checkpoint.
        """
        started = time.monotonic()
        root = _canonical_path(str(artifact_root)); _enforce_root(root, "PREPROCESS_ARTIFACT_ROOT")
        lock_path = root / ".preprocess.lock"
        with lock_path.open("a+") as lock:
            fcntl.flock(lock.fileno(), fcntl.LOCK_EX)
            current = self.get_status(version_id, root, _lock=False)
            if current["status"] == "READY":
                raise ValueError("PUBLISHED_VERSION_IMMUTABLE")
            wanted = sorted(set(int(p) for p in pages))
            _dev_log("dev_preprocessing_retry stage=started versionId=%s requestId=%s selectedPageCount=%d confirmedLayoutPageCount=%d outcome=started",
                     version_id, request_id, len(wanted), len(layout_selections or {}))
            layout_selections = layout_selections or {}
            by_number = {int(p["page_number"]): p for p in current["pages"]}
            if not wanted or any(p not in by_number for p in wanted):
                raise ValueError("INVALID_PAGE_SELECTION")
            if set(layout_selections) - set(wanted) or (confirmed_by and not layout_selections):
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
            resume_promotion = isinstance(retry_index.get(idem), dict) and retry_index[idem].get("state") == "promotion_pending" and all(
                p.get("status") in {"VALIDATED", "EXCLUDED_FORM"} for p in current["pages"])
            updated = []
            recovered_pages: dict[str, Mapping[str, Any]] = dict(persisted_recovered)
            source_path = Path(str(current.get("manifest", {}).get("source", {}).get("path", "")))
            raw_pages: list[Mapping[str, Any]] | None = None
            heading_reference_font_size: float | None = None
            for page in (() if resume_promotion else current["pages"]):
                item = dict(page)
                previous_status = item.get("status")
                if item.get("status") == "EXCLUDED_FORM":
                    if item["page_number"] in wanted:
                        raise ValueError("EXCLUDED_FORM_PAGE_IMMUTABLE")
                    updated.append(item)
                    continue
                if item["page_number"] in wanted:
                    selected_regions = layout_selections.get(item["page_number"], {})
                    page_key = str(item["page_number"])
                    was_validated = item.get("status") == "VALIDATED"
                    if was_validated:
                        persisted_page = persisted_recovered.get(page_key, {})
                        if not isinstance(persisted_page, Mapping):
                            persisted_page = {}
                        confirmation = persisted_page.get("layout_confirmation")
                        confirmation = confirmation if isinstance(confirmation, Mapping) else {}
                        same_confirmation = (
                            confirmation.get("admin_id") == confirmed_by
                            and confirmation.get("candidate_version") == version_id
                            and confirmation.get("selections") == selected_regions
                            and confirmation.get("source_sha256") == current.get("manifest", {}).get("source", {}).get("sha256")
                            and confirmation.get("page_layout_fingerprint") == _page_layout_fingerprint(persisted_page)
                            and confirmation.get("layout_analysis_version") == _LAYOUT_ANALYSIS_VERSION
                        )
                        if persisted_page and (not selected_regions or same_confirmation):
                            if not selected_regions:
                                updated.append(item)
                                continue
                            if same_confirmation:
                                _dev_log("dev_preprocessing_retry stage=confirmed_layout_reuse pageNumber=%d sourceHashMatched=true layoutFingerprintMatched=true analysisVersion=%s outcome=reused",
                                         item["page_number"], _LAYOUT_ANALYSIS_VERSION)
                                updated.append(item)
                                continue
                            _dev_log("dev_preprocessing_retry stage=confirmed_layout_reuse pageNumber=%d sourceHashMatched=%s layoutFingerprintMatched=%s analysisVersionMatched=%s outcome=recompute",
                                     item["page_number"],
                                     confirmation.get("source_sha256") == current.get("manifest", {}).get("source", {}).get("sha256"),
                                     confirmation.get("page_layout_fingerprint") == _page_layout_fingerprint(persisted_page),
                                     confirmation.get("layout_analysis_version") == _LAYOUT_ANALYSIS_VERSION)
                        if not persisted_page:
                            raise ValueError("VALIDATED_PAGE_IMMUTABLE")
                    if selected_regions:
                        recovered_pages.pop(page_key, None)
                        item["status"] = "NEEDS_REVIEW"
                        item.pop("layout_confirmation", None)
                    attempt = self.retry_policy.request(item)
                    history = list(item.get("attempt_history", [])); history.append(attempt.as_dict())
                    item["attempts"] = attempt.attempt_number; item["attempt_history"] = history
                    item["diagnostics"] = {"strategy": attempt.strategy, "regions": [list(r) for r in attempt.regions], "findings": list(attempt.findings)}
                    # Re-run the native adapter for this page only.  A failed
                    # extraction remains NEEDS_REVIEW and is never guessed
                    # into READY.
                    try:
                        if not source_path.is_file(): raise ValueError("SOURCE_NOT_FOUND")
                        if raw_pages is None:
                            extraction_started = time.monotonic()
                            try:
                                raw_pages = (self.native_pdf.extract(source_path, page_numbers=tuple(wanted))
                                             if isinstance(self.native_pdf, PyMuPdfNativePdfAdapter)
                                             else self.native_pdf.extract(source_path))
                            except Exception as exc:
                                _dev_log("dev_preprocessing_retry stage=source_extraction_failed versionId=%s requestId=%s selectedPageCount=%d failureType=%s elapsedMs=%d outcome=failed",
                                         version_id, request_id, len(wanted), type(exc).__name__,
                                         round((time.monotonic() - extraction_started) * 1000))
                                raise
                            _dev_log("dev_preprocessing_retry stage=source_extraction_complete versionId=%s requestId=%s selectedPageCount=%d extractedPageCount=%d elapsedMs=%d outcome=completed",
                                     version_id, request_id, len(wanted), len(raw_pages),
                                     round((time.monotonic() - extraction_started) * 1000))
                            if self.analyze_structure:
                                heading_reference_font_size = document_reference_font_size(raw_pages)
                        raw = next((p for p in raw_pages if isinstance(p, Mapping) and p.get("page_number") == item["page_number"]), None)
                        geometry = raw.get("geometry", {}) if isinstance(raw, Mapping) else {}
                        # Retry must use the same OCR-capable extraction path
                        # as the first attempt.  Re-running native PDF
                        # extraction alone cannot recover image-only pages.
                        if isinstance(raw, Mapping):
                            raw = self._augment_with_ocr(source_path, raw, item["page_number"])
                        boxes = raw.get("blocks", ()) if isinstance(raw, Mapping) else ()
                        valid = isinstance(raw, Mapping) and float(geometry.get("width", 0)) > 0 and float(geometry.get("height", 0)) > 0 and all(isinstance(b, Mapping) and len(b.get("bbox", ())) == 4 and 0 <= float(b["bbox"][0]) <= float(b["bbox"][2]) <= float(geometry["width"]) and 0 <= float(b["bbox"][1]) <= float(b["bbox"][3]) <= float(geometry["height"]) for b in boxes)
                        if isinstance(raw, Mapping) and raw.get("capability_error"):
                            valid = False
                            item["findings"] = [str(raw["capability_error"])]
                        if valid:
                            page_geometry = PageGeometry(float(geometry["width"]), float(geometry["height"]))
                            if not self.analyze_structure:
                                if selected_regions:
                                    raise ValueError("LAYOUT_SELECTION_UNSUPPORTED_IN_TEXT_MODE")
                                valid = any(str(block.get("text", "")).strip() for block in boxes
                                            if isinstance(block, Mapping))
                                if not valid:
                                    item["findings"] = ["NO_USABLE_TEXT"]
                            elif selected_regions:
                                candidate_plan = ReadingOrderPlanner().plan(boxes, page_geometry)
                                current_profiles = {profile.region_id: profile for profile in candidate_plan.profiles}
                                if set(selected_regions) != set(current_profiles) or any(
                                    type(candidate) is not int or candidate < 0
                                    or candidate >= len(current_profiles[region].candidates)
                                    for region, candidate in selected_regions.items()
                                ):
                                    raise ValueError("INVALID_LAYOUT_CANDIDATE")
                            if self.analyze_structure:
                                planner = ReadingOrderPlanner()
                                layout_plan = planner.plan(boxes, page_geometry, selected_regions)
                            if self.analyze_structure and not selected_regions and attempt.strategy == "alternate-column-hypothesis":
                                alternate = {}
                                for profile in layout_plan.profiles:
                                    ranked = sorted(enumerate(profile.candidates),
                                                    key=lambda pair: pair[1].score, reverse=True)
                                    if len(ranked) >= 2 and ranked[0][1].score - ranked[1][1].score >= .08:
                                        alternate[profile.region_id] = ranked[0][0]
                                if alternate:
                                    _dev_log("dev_preprocessing_retry stage=alternate_layout_candidate pageNumber=%d candidateRegionCount=%d minimumScoreGap=0.08 outcome=attempted",
                                             item["page_number"], len(alternate))
                                    layout_plan = planner.plan(boxes, page_geometry, alternate)
                                else:
                                    _dev_log("dev_preprocessing_retry stage=alternate_layout_candidate pageNumber=%d candidateRegionCount=0 minimumScoreGap=0.08 outcome=needs_review",
                                             item["page_number"])
                                    valid = False
                            if self.analyze_structure and confirmed_by and selected_regions:
                                previous = item.get("layout_review") or {}
                                old_layout = persisted_page.get("layout", {}) if was_validated else {}
                                prior_profiles = {profile["region_id"]: profile for profile in
                                    previous.get("profiles", old_layout.get("profiles", ()))}
                                new_profiles = {profile.region_id: to_dict(profile) for profile in layout_plan.profiles}
                                if set(selected_regions) != set(new_profiles) or any(
                                    region not in prior_profiles or
                                    prior_profiles[region].get("candidates") != new_profiles[region].get("candidates")
                                    for region in selected_regions
                                ):
                                    raise ValueError("INVALID_LAYOUT_CANDIDATE")
                                item["layout_confirmation"] = {"admin_id": confirmed_by,
                                    "candidate_version": version_id, "selections": selected_regions,
                                    "source_sha256": current.get("manifest", {}).get("source", {}).get("sha256"),
                                    "page_layout_fingerprint": _page_layout_fingerprint(raw),
                                    "layout_analysis_version": _LAYOUT_ANALYSIS_VERSION}
                                raw["layout_confirmation"] = item["layout_confirmation"]
                            if self.analyze_structure and layout_plan.ambiguous:
                                valid = False
                            if self.analyze_structure:
                                raw = {**raw, "layout": to_dict(layout_plan)}
                                associations, candidates = HeadingAssociator().analyze(boxes, layout_plan, heading_reference_font_size)
                                raw["heading_associations"] = to_dict(associations)
                                raw["heading_candidates"] = to_dict(candidates)
                                raw["tables"] = to_dict(TableStructureDetector().detect(boxes))
                                render = self._render_evidence(source_path, item["page_number"], page_geometry)
                                validation = self.layout_validator.validate(raw, render,
                                    confirmed_columns=bool(confirmed_by and selected_regions))
                                valid = validation.valid
                                if not valid:
                                    item["findings"] = [finding.code for finding in validation.findings
                                        if finding.severity == "error"]
                        if valid:
                            if confirmed_by and selected_regions and isinstance(item.get("layout_confirmation"), Mapping):
                                _save_layout_approval(root,
                                    str(current.get("manifest", {}).get("source", {}).get("sha256", "")),
                                    item["page_number"], raw, item["layout_confirmation"])
                                _dev_log("dev_preprocessing_layout_approval stage=registry_write pageNumber=%d confirmerPresent=true outcome=saved",
                                         item["page_number"])
                            item["status"] = "VALIDATED"; item["findings"] = []
                            history[-1] = {**history[-1], "status": "VALIDATED", "findings": []}
                            raw["_retry_attempts"] = item["attempts"]
                            raw["_retry_attempt_history"] = history
                            recovered_pages[str(item["page_number"])] = raw
                        else:
                            item["findings"] = item.get("findings") or ["RETRY_EXTRACTION_FAILED"]
                            if isinstance(raw, Mapping):
                                item["layout_review"] = _layout_review_payload(raw)
                    except Exception as exc:
                        if str(exc) == "INVALID_LAYOUT_CANDIDATE":
                            raise
                        item["findings"] = [str(exc) or "RETRY_EXTRACTION_FAILED"]
                    if item["status"] != "VALIDATED":
                        item["status"] = ("USABLE_WITH_WARNINGS" if previous_status == "USABLE_WITH_WARNINGS"
                                           else "NEEDS_REVIEW")
                        item["attempt_history"][-1] = {**item["attempt_history"][-1],
                            "status": item["status"], "findings": item.get("findings", [])}
                    item["diagnostics"]["finding_regions"] = item.get("finding_regions", item["diagnostics"].get("regions", []))
                    item["diagnostics"]["overlay"] = f"diagnostics/{version_id}/page-{item['page_number']}-attempt-{attempt.attempt_number}.json"
                updated.append(item)
            if resume_promotion:
                updated = [dict(p) for p in current["pages"]]
            response = {**current, "operation": "retry_pages", "request_id": request_id, "pages": updated}
            statuses = [p["status"] for p in updated]
            response["page_summary"] = {**current["page_summary"],
                                        "validated": statuses.count("VALIDATED"),
                                        "usable_with_warnings": statuses.count("USABLE_WITH_WARNINGS"),
                                        "excluded_forms": statuses.count("EXCLUDED_FORM"),
                                        "needs_review": statuses.count("NEEDS_REVIEW"),
                                        "ready": statuses.count("VALIDATED") + statuses.count("USABLE_WITH_WARNINGS") + statuses.count("EXCLUDED_FORM")}
            _dev_log("dev_preprocessing_retry stage=page_checkpoint_complete versionId=%s requestId=%s expectedSelectedPageCount=%d actualValidatedPageCount=%d actualNeedsReviewPageCount=%d elapsedMs=%d outcome=checkpointed",
                     version_id, request_id, len(wanted), statuses.count("VALIDATED"),
                     statuses.count("NEEDS_REVIEW"), round((time.monotonic() - started) * 1000))
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
            if all(item.get("status") in {"VALIDATED", "EXCLUDED_FORM"} for item in updated):
                manifest_source = current.get("manifest", {}).get("source", {})
                promoted_id = f"{version_id}-retry-{hashlib.sha256(idem.encode()).hexdigest()[:8]}"
                promoted_pages = {int(item["page_number"]): item for item in updated}
                promoted_recovered = dict(recovered_pages)
                for key, recovered_page in promoted_recovered.items():
                    page_state = promoted_pages.get(int(key))
                    if page_state is None or page_state.get("status") != "VALIDATED" or not isinstance(recovered_page, Mapping):
                        continue
                    recovered_page = dict(recovered_page)
                    confirmation = page_state.get("layout_confirmation")
                    if isinstance(confirmation, Mapping):
                        recovered_page["layout_confirmation"] = confirmation
                    else:
                        recovered_page.pop("layout_confirmation", None)
                    if isinstance(page_state.get("layout_review"), Mapping):
                        recovered_page["layout_review"] = page_state["layout_review"]
                    recovered_page["_retry_attempts"] = page_state.get("attempts", 1)
                    recovered_page["_retry_attempt_history"] = page_state.get("attempt_history", [])
                    promoted_recovered[key] = recovered_page
                promoted_request = {"request_id": f"{request_id}-publish", "source_path": manifest_source.get("path"),
                                    "source_sha256": manifest_source.get("sha256"),
                                    "policy_version": current.get("manifest", {}).get("policy", {}).get("version", "retry"),
                                    "output_dir": str(root), "version_id": promoted_id,
                                    "retry_source_version_id": version_id,
                                    "recovered_pages": promoted_recovered}
                try:
                    promoted = self._preprocess_locked(promoted_request, root)
                    if promoted.get("status") in {"READY", "PARTIAL"}:
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
    def _artifact_refs(directory: Path, published: bool) -> dict[str, Any]:
        names = ["manifest.json", "version.json"] + (["chunks.jsonl", "document_tree.json", "issues.jsonl"] if published else [])
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


def _dev_log(message: str, *args: Any) -> None:
    if os.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "").lower() in {"1", "true", "yes", "on"}:
        _LOGGER.info(message, *args)


def _merge_vision_table_blocks(
        source_blocks: list[dict[str, Any]], table_blocks: list[dict[str, Any]],
        table_regions: list[tuple[float, float, float, float]]) -> tuple[list[dict[str, Any]], int, int]:
    """Replace only native text blocks whose contents the extracted cells cover."""
    cell_tokens = {token for block in table_blocks for token in re.findall(r"\w+", str(block.get("text", "")).casefold())}
    kept: list[dict[str, Any]] = []
    replaced = 0
    preserved_overlap = 0
    for block in source_blocks:
        box = block.get("bbox")
        if block.get("kind") == "table_cell" or not _center_in_any(box, table_regions):
            kept.append(block)
            continue
        tokens = re.findall(r"\w+", str(block.get("text", "")).casefold())
        coverage = sum(token in cell_tokens for token in tokens) / len(tokens) if tokens else 0.0
        if coverage >= 0.8:
            replaced += 1
        else:
            kept.append(block)
            preserved_overlap += 1
    return [*kept, *table_blocks], replaced, preserved_overlap


def _center_in_any(box: Any, regions: list[tuple[float, float, float, float]]) -> bool:
    if not isinstance(box, (list, tuple)) or len(box) != 4:
        return False
    x = (float(box[0]) + float(box[2])) / 2
    y = (float(box[1]) + float(box[3])) / 2
    return any(x0 <= x <= x1 and y0 <= y <= y1 for x0, y0, x1, y1 in regions)
