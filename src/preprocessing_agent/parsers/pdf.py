"""PDF extraction adapter with an injectable extraction seam.

The optional PDF libraries are deliberately kept at the adapter boundary. Tests
and callers may provide an extractor returning page/block mappings without
installing a particular PDF implementation.
"""

from __future__ import annotations

import hashlib
import contextlib
import json
import logging
import math
import os
import re
import sys
import time
from collections import Counter
from pathlib import Path
from typing import Any, Callable, Iterable, Mapping

from preprocessing_agent.domain import ParsedBlock, ParsedDocument, ParsedPage, SourceSpan, ReadingOrderPlan, from_dict, schema_path, validate_json
from preprocessing_agent.layout import ReadingOrderPlanner
from preprocessing_agent.layout.page_classification import form_geometry_counts, is_form_like
from preprocessing_agent.domain.serialization import to_dict
from .base import ParserError
from .normalize import normalize_text


Extractor = Callable[[Path], Iterable[Mapping[str, Any]]]
_LOGGER = logging.getLogger(__name__)


class PdfDocumentParser:
    def __init__(self, extractor: Extractor | None = None, layout_selections: dict[int, dict[str, int]] | None = None,
                 *, analyze_structure: bool = True, provider_name: str | None = None) -> None:
        provider = os.getenv("PREPROCESSING_PDF_PROVIDER", "hybrid").strip().casefold() if extractor is None else "injected"
        self._extractor = extractor or _provider_extractor()
        self._provider_name = provider_name or provider
        self._layout_selections = layout_selections or {}
        self._analyze_structure = analyze_structure

    def parse(self, source: Path) -> ParsedDocument:
        source = Path(source)
        try:
            raw_pages = tuple(self._extractor(source))
        except Exception as exc:  # normalize adapter errors at the port boundary
            raise ParserError(f"failed to parse {source}: {exc}") from exc
        pdf_bookmarks = tuple(raw_pages[0].get("pdf_bookmarks", ())) if raw_pages and self._analyze_structure else ()
        if self._analyze_structure:
            from preprocessing_agent.structure.references import extract_printed_contents_entries
            printed_contents = tuple(extract_printed_contents_entries(raw_pages))
            from preprocessing_agent.structure import HeadingAssociator, TableStructureDetector, document_reference_font_size
            reference_font_size = document_reference_font_size(raw_pages)
        else:
            printed_contents = ()
            reference_font_size = None
            _layout_log("dev_preprocessing_parser stage=started pageCount=%d expectedAnalysis=disabled actualAnalysis=disabled route=source_text_and_provenance outcome=started",
                        len(raw_pages))
        pages: list[ParsedPage] = []
        layout_diagnostics: list[dict[str, Any]] = []
        document_parts: list[str] = []
        excluded_form_pages: list[int] = []
        excluded_form_evidence: list[dict[str, Any]] = []
        for page_position, raw_page in enumerate(raw_pages, start=1):
            page_started = time.monotonic()
            page_number = int(raw_page.get("page_number", page_position))
            form_diagnostics = raw_page.get("form_diagnostics")
            required_form_counts = {"drawing_count", "aligned_line_count", "long_horizontal_line_count", "text_line_count"}
            if (raw_page.get("page_classification") == "form"
                    and raw_page.get("form_content_state", "blank") == "blank"
                    and isinstance(form_diagnostics, Mapping)
                    and required_form_counts <= form_diagnostics.keys()
                    and all(type(form_diagnostics[key]) is int and form_diagnostics[key] >= 0
                            for key in required_form_counts)
                    and is_form_like(**{key: form_diagnostics[key] for key in required_form_counts})):
                excluded_form_pages.append(page_number)
                excluded_form_evidence.append({"page_number": page_number, "page_classification": "form",
                                               "form_diagnostics": {key: form_diagnostics[key]
                                                                    for key in sorted(required_form_counts)}})
                _layout_log("dev_preprocessing_form stage=parser_exclusion pageNumber=%d expectedFormEvidenceFields=4 actualFormEvidenceFields=%d route=skip_page_chunks outcome=excluded",
                            page_number, len(form_diagnostics.keys()))
                continue
            # Keep source identity stable across geometric projection. The
            # ordinal is provenance, not a reading-order signal.
            source_blocks = _deduplicate_blocks(tuple(
                ({**raw_block, "block_id": str(raw_block.get("block_id", f"p{page_number}-b{index}"))})
                for index, raw_block in enumerate(raw_page.get("blocks", ()))
            ))
            missing_geometry = any(raw_block.get("bbox") is None for raw_block in source_blocks)
            if not self._analyze_structure:
                plan = None if missing_geometry else ReadingOrderPlanner().plan(
                    source_blocks, selections=self._layout_selections.get(page_number, {}))
                if plan is None:
                    raw_blocks = source_blocks
                else:
                    by_id = {str(block["block_id"]): block for block in source_blocks}
                    raw_blocks = tuple(by_id[block_id]
                                       for block_id in (*plan.ordered_block_ids, *plan.furniture_block_ids))
                    if plan.ambiguous:
                        layout_diagnostics.append({"page_number": page_number, "layout": to_dict(plan)})
                _layout_log("dev_preprocessing_parser stage=page_source pageNumber=%d expectedBlockCount=%d actualBlockCount=%d expectedLocatedBlockCount=%d actualLocatedBlockCount=%d route=%s outcome=%s",
                            page_number, len(source_blocks), len(raw_blocks),
                            sum(raw_block.get("bbox") is not None for raw_block in source_blocks),
                            sum(raw_block.get("bbox") is not None for raw_block in raw_blocks),
                            "geometry_reading_order_only" if plan is not None else "source_order_missing_geometry",
                            "ambiguous_but_ordered" if plan is not None and plan.ambiguous else
                            "complete" if not missing_geometry else "missing_provenance")
            else:
                layout_value = raw_page.get("layout")
                serialized_plan = layout_value if isinstance(layout_value, Mapping) and any(
                    key in layout_value for key in ("ordered_block_ids", "regions", "profiles", "table_zones")) else None
                if serialized_plan is not None:
                    plan = _validated_plan(serialized_plan, source_blocks)
                    _layout_log("dev_preprocessing_layout stage=plan_consumed pageNumber=%d blockCount=%d tableSourceBlockCount=%d orderedBlockCount=%d furnitureBlockCount=%d route=serialized_plan elapsedMs=%d outcome=accepted",
                                 page_number, len(source_blocks), sum(_is_table_block(block) for block in source_blocks),
                                 len(plan.ordered_block_ids), len(plan.furniture_block_ids),
                                 round((time.monotonic() - page_started) * 1000))
                else:
                    plan = None if missing_geometry else (
                        ReadingOrderPlanner().form_plan(source_blocks)
                        if raw_page.get("page_classification") == "form" else
                        ReadingOrderPlanner().plan(source_blocks, selections=self._layout_selections.get(page_number, {})))
                    _layout_log("dev_preprocessing_layout stage=plan_complete pageNumber=%d blockCount=%d tableSourceBlockCount=%d route=%s elapsedMs=%d outcome=%s",
                                 page_number, len(source_blocks), sum(_is_table_block(block) for block in source_blocks),
                                 "recomputed" if plan is not None else "missing_geometry",
                                 round((time.monotonic() - page_started) * 1000), "success" if plan is not None else "review")
                if missing_geometry and plan is None:
                    # Text-only callers remain compatible, but the missing
                    # geometry is explicit evidence and cannot be READY-published.
                    raw_blocks = source_blocks
                    layout_diagnostics.append({"page_number": page_number, "finding": "LAYOUT_GEOMETRY_REQUIRED"})
                elif plan.ambiguous:
                    # Use the planner's deterministic geometric diagnostic order,
                    # never the extractor/source order. The application gate still
                    # blocks this page from READY publication.
                    by_id = {str(block["block_id"]): block for block in source_blocks}
                    raw_blocks = tuple(by_id[block_id] for block_id in (*plan.ordered_block_ids, *plan.furniture_block_ids))
                    layout_diagnostics.append({"page_number": page_number, "layout": to_dict(plan)})
                else:
                    by_id = {str(block["block_id"]): block for block in source_blocks}
                    raw_blocks = tuple(by_id[block_id] for block_id in (*plan.ordered_block_ids, *plan.furniture_block_ids))
            blocks: list[ParsedBlock] = []
            page_parts: list[str] = []
            char_cursor = 0
            for block_position, raw_block in enumerate(raw_blocks):
                text = _block_text(raw_block)
                if not text:
                    continue
                # Sort only when the extractor explicitly supplies reading order;
                # otherwise its block order is authoritative and deterministic.
                block_id = str(raw_block["block_id"])
                table_id = (str(raw_block["table_id"]) if raw_block.get("table_id") else
                            "table-1" if _is_table_block(raw_block) else None)
                row = raw_block.get("row")
                column = raw_block.get("column")
                table_cell = f"r{row}:c{column}" if table_id and row is not None and column is not None else None
                bbox = _bbox(raw_block.get("bbox"))
                span = SourceSpan(page_number, block_position, char_cursor, char_cursor + len(text),
                                  bbox=bbox, table_id=table_id, table_cell=table_cell)
                block = ParsedBlock(
                    block_id=block_id,
                    source_text=text,
                    source_span=span,
                    bbox=bbox,
                    font_size=_number(raw_block.get("font_size", raw_block.get("size"))),
                    font_weight=_font_weight(raw_block),
                    extraction_method=str(raw_block.get("extraction_method", "native")),
                    text_confidence=float(raw_block.get("text_confidence", raw_block.get("confidence", 1.0))),
                    line_boxes=tuple(tuple(float(value) for value in line["bbox"])
                                     for line in raw_block.get("lines", ())
                                     if isinstance(line, Mapping) and len(line.get("bbox", ())) == 4),
                )
                blocks.append(block)
                page_parts.append(text)
                char_cursor += len(text) + 1
            page_text = normalize_text("\n".join(page_parts))
            blocks_by_id = {block.block_id: block for block in blocks}
            page_structure = tuple({**raw_block, "source_text": blocks_by_id[str(raw_block["block_id"])].source_text} for raw_block in raw_blocks if str(raw_block["block_id"]) in blocks_by_id)
            heading_started = time.monotonic()
            headings, heading_candidates = (HeadingAssociator().analyze(page_structure, plan, reference_font_size)
                                            if self._analyze_structure and plan is not None else ((), ()))
            if self._analyze_structure:
                _layout_log("dev_preprocessing_heading stage=candidate_analysis pageNumber=%d blockCount=%d expectedReferenceFontSize=available actualReferenceFontSize=%s candidateCount=%d acceptedCount=%d rejectedCount=%d route=line_and_span_candidates elapsedMs=%d outcome=%s",
                             page_number, len(page_structure), reference_font_size, len(heading_candidates),
                             sum(candidate.accepted for candidate in heading_candidates),
                             sum(not candidate.accepted for candidate in heading_candidates),
                             round((time.monotonic() - heading_started) * 1000), "success" if plan is not None else "skipped_missing_layout")
            explicit_table_blocks = tuple(block for block in page_structure if _is_table_block(block))
            explicit_table_geometry_available = bool(explicit_table_blocks) and all(
                _bbox(block.get("bbox")) is not None for block in explicit_table_blocks)
            if self._analyze_structure and plan is not None:
                tables = TableStructureDetector().detect(page_structure)
            elif explicit_table_geometry_available:
                from preprocessing_agent.structure import TableStructureDetector
                tables = TableStructureDetector().detect(explicit_table_blocks)
                _layout_log("dev_preprocessing_table_structure stage=explicit_cells pageNumber=%d expectedCellBlockCount=%d actualStructuredTableCount=%d route=table_cells_without_heading_analysis outcome=%s",
                             page_number, len(explicit_table_blocks), len(tables),
                             "success" if tables else "preserved_as_text")
            else:
                tables = ()
            pages.append(ParsedPage(page_number, tuple(blocks), page_text, headings, tables,
                                   str(raw_page.get("page_classification", "text-native")), layout_plan=plan,
                                   heading_candidates=heading_candidates,
                                   native_table_diagnostics=tuple(raw_page.get("native_table_diagnostics", ()))))
            document_parts.append(page_text)
        source_text = normalize_text("\n".join(document_parts))
        document_id = hashlib.sha256(source_text.encode("utf-8")).hexdigest()[:16]
        metadata = {"format": "pdf", "pdf_provider": self._provider_name,
                    "source_page_count": len(raw_pages),
                    "structure_analysis": self._analyze_structure,
                    "excluded_form_pages": excluded_form_pages,
                    "excluded_form_evidence": excluded_form_evidence}
        if layout_diagnostics:
            metadata["layout_diagnostics"] = layout_diagnostics
        document = ParsedDocument(document_id, str(source), source_text, tuple(pages), metadata)
        if self._analyze_structure:
            from preprocessing_agent.structure import DocumentHierarchyResolver
            from preprocessing_agent.structure.references import collect_document_structure_references
            document = DocumentHierarchyResolver().resolve(document)
            return collect_document_structure_references(document, pdf_bookmarks, printed_contents)
        _layout_log("dev_preprocessing_parser stage=completed pageCount=%d expectedSourcePageCount=%d actualSourcePageCount=%d route=source_text_and_provenance outcome=success",
                    len(pages), len(raw_pages), len(pages))
        return document


def _bbox(value: Any) -> tuple[float, float, float, float] | None:
    if value is None:
        return None
    result = tuple(float(item) for item in value)
    if len(result) != 4:
        raise ParserError("PDF block bbox must contain four coordinates")
    return result  # type: ignore[return-value]


def _is_table_block(block: Mapping[str, Any]) -> bool:
    return bool(block.get("table_id")) or str(block.get("kind", "")).casefold() in {"table", "table_cell", "cell"}


def _validated_plan(value: Any, blocks: tuple[Mapping[str, Any], ...]) -> ReadingOrderPlan:
    started = time.monotonic()
    ids = [str(block["block_id"]) for block in blocks]
    try:
        if not isinstance(value, Mapping):
            raise ValueError("serialized layout plan must be an object")
        validate_json(dict(value), json.loads(schema_path("layout-extraction.schema.json").read_text()))
        plan = from_dict(ReadingOrderPlan, dict(value))
        projected = [*plan.ordered_block_ids, *plan.furniture_block_ids]
        table_ids = [block_id for zone in plan.table_zones for block_id in zone.block_ids]
        explicit_table_ids = [str(block["block_id"]) for block in blocks if _is_table_block(block)]
        if (len(ids) != len(set(ids)) or len(projected) != len(ids) or set(projected) != set(ids)
                or len(table_ids) != len(set(table_ids)) or set(table_ids) != set(explicit_table_ids)):
            raise ValueError(f"layout source ownership mismatch expectedBlockCount={len(ids)} actualProjectedCount={len(projected)} expectedTableSourceCount={len(explicit_table_ids)} actualTableZoneCount={len(table_ids)}")
        region_ids = [block_id for region in plan.regions for block_id in region.block_ids]
        if len(region_ids) != len(set(region_ids)) or set(region_ids) != set(ids) - set(explicit_table_ids):
            raise ValueError(f"layout prose ownership mismatch expectedProseBlockCount={len(ids) - len(explicit_table_ids)} actualRegionBlockCount={len(region_ids)}")
        return plan
    except (TypeError, KeyError, ValueError) as exc:
        _layout_log("dev_preprocessing_layout stage=plan_validation pageBlockCount=%d tableSourceBlockCount=%d elapsedMs=%d failureType=%s failure=%s recoveryAction=needs_review outcome=failed",
                    len(blocks), sum(_is_table_block(block) for block in blocks),
                    round((time.monotonic() - started) * 1000), type(exc).__name__, str(exc))
        raise ParserError("INVALID_SERIALIZED_LAYOUT_PLAN") from exc


def _layout_log(message: str, *args: Any) -> None:
    if os.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "").lower() in {"1", "true", "yes", "on"}:
        _LOGGER.info(message, *args)


def _deduplicate_blocks(blocks: tuple[Mapping[str, Any], ...]) -> tuple[Mapping[str, Any], ...]:
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


def _number(value: Any) -> float | None:
    return None if value is None else float(value)


def _font_weight(block: Mapping[str, Any]) -> str | None:
    value = block.get("font_weight", block.get("weight"))
    if value is not None:
        return str(value)
    font = str(block.get("font", ""))
    return "bold" if "bold" in font.casefold() else (font or None)


def _block_text(block: Mapping[str, Any]) -> str:
    """Preserve extracted text while restoring geometry-implied span boundaries."""
    text = block.get("text", block.get("source_text"))
    if text is not None:
        return str(text)
    lines = block.get("lines", ())
    joined_lines = []
    for line in lines:
        spans = sorted(line.get("spans", ()), key=lambda item: (_span_bbox(item)[0], _span_bbox(item)[1]))
        parts: list[str] = []
        previous = None
        for span in spans:
            value = str(span.get("text", ""))
            if not value:
                continue
            if previous is not None and not previous[-1:].isspace() and not value[:1].isspace():
                parts.append(" ")
            parts.append(value)
            previous = value
        if parts:
            joined_lines.append("".join(parts))
    return "\n".join(joined_lines)


def _span_bbox(span: Mapping[str, Any]) -> tuple[float, float, float, float]:
    value = span.get("bbox", (0, 0, 0, 0))
    return tuple(float(item) for item in value)  # type: ignore[return-value]


def _order_blocks(blocks: tuple[Mapping[str, Any], ...]) -> tuple[Mapping[str, Any], ...]:
    """Project blocks through the regional layout planner."""
    if any(_bbox(block.get("bbox")) is None for block in blocks):
        return blocks
    plan = ReadingOrderPlanner().plan(blocks)
    if plan.ambiguous:
        # Deterministic geometry is diagnostic only; the application gate must
        # reject the corresponding page as NEEDS_REVIEW.
        return tuple(sorted(blocks, key=lambda block: (_bbox(block["bbox"])[1], _bbox(block["bbox"])[0], str(block.get("block_id", "")))))
    by_id = {str(block.get("block_id", f"block-{index}")): block for index, block in enumerate(blocks)}
    return tuple(by_id[block_id] for block_id in plan.ordered_block_ids if block_id in by_id)


def _default_extractor(source: Path) -> Iterable[Mapping[str, Any]]:
    try:
        with contextlib.redirect_stdout(sys.stderr):
            import fitz  # type: ignore
    except ImportError:
        raise ParserError("install PyMuPDF or provide an extractor")
    with fitz.open(source) as document:
        for page_number, page in enumerate(document, start=1):
            blocks = []
            generated_block_id_count = 0
            for block_index, block in enumerate(page.get_text("dict").get("blocks", [])):
                lines = block.get("lines", [])
                spans = [span for line in lines for span in line.get("spans", [])]
                text = _block_text(block).strip()
                if not text:
                    continue
                block_id = str(block.get("block_id") or f"p{page_number}-b{block_index}")
                generated_block_id_count += not bool(block.get("block_id"))
                first = spans[0] if spans else {}
                line_values = []
                cursor = 0
                for line in lines:
                    line_spans = line.get("spans", [])
                    line_parts: list[str] = []
                    previous_text = ""
                    for span in line_spans:
                        span_text = str(span.get("text", ""))
                        if span_text and previous_text and not previous_text[-1:].isspace() and not span_text[:1].isspace():
                            line_parts.append(" ")
                        line_parts.append(span_text)
                        if span_text:
                            previous_text = span_text
                    line_text = "".join(line_parts)
                    line_start = text.find(line_text, cursor) if line_text else -1
                    if line_start < 0:
                        line_start = cursor
                    line_end = min(len(text), line_start + len(line_text))
                    detailed_spans = []
                    for span_index, span in enumerate(line_spans):
                        font_name = str(span.get("font", ""))
                        detailed_spans.append({"text": span.get("text", ""), "bbox": span.get("bbox"),
                            "font_size": span.get("size"), "font": font_name,
                            "font_weight": "bold" if "bold" in font_name.casefold() else font_name,
                            "char_start": line_start, "char_end": line_end, "span_index": span_index})
                    line_values.append({"text": line_text, "bbox": line.get("bbox"),
                        "char_start": line_start, "char_end": line_end, "spans": detailed_spans})
                    cursor = line_end
                blocks.append({"block_id": block_id, "text": text, "bbox": block.get("bbox"), "font_size": first.get("size"), "font": first.get("font"),
                               "spans": [{"text": span.get("text", ""), "bbox": span.get("bbox"), "font_size": span.get("size"),
                                          "font": span.get("font"), "font_weight": "bold" if "bold" in str(span.get("font", "")).casefold() else str(span.get("font", ""))}
                                         for span in spans],
                               "lines": line_values})
            _layout_log("dev_preprocessing_block_identity stage=completed pageNumber=%d expectedIdentifiedBlockCount=%d actualBlockCount=%d generatedBlockIdCount=%d route=page_and_source_block_ordinal outcome=%s",
                        page_number, len(blocks), len(blocks), generated_block_id_count,
                        "success" if all(block.get("block_id") for block in blocks) else "failed")
            text_line_count = sum(len(block.get("lines", ())) for block in blocks)
            form_diagnostics = form_geometry_counts(page.get_drawings(), text_line_count)
            page_classification = "form" if is_form_like(**form_diagnostics) else "text-native"
            # Preserve native table structure when the installed PyMuPDF
            # exposes find_tables(); unsupported versions simply yield no
            # table cells and retain the normal text extraction path.
            found_tables = _find_native_tables(page, page_number)
            reconstructed = []
            for table, strategy in found_tables:
                projected, diagnostic = _reconstruct_native_table(table, blocks, page_number)
                diagnostic.update(detection_strategies=strategy.split("+"), detection_deduplicated=False)
                _classify_text_detection_hypothesis(diagnostic, strategy, page_number)
                reconstructed.append((projected, diagnostic))
            selected, table_diagnostics = _reconcile_native_table_candidates(found_tables, reconstructed, page_number)
            for candidate_index, diagnostic in enumerate(table_diagnostics):
                projected = reconstructed[candidate_index][0]
                _layout_log("dev_preprocessing_layout stage=native_table_reconstruction pageNumber=%d candidateIndex=%d strategies=%s rowCount=%d reconstructedRowCount=%d columnCount=%d expectedCellCount=%d actualProjectedCellCount=%d sourceSpanCount=%d mappedSpanCount=%d sourceTokenCount=%d projectedTokenCount=%d textMatchesGrid=%s emptyRowCount=%d alignedFollowingSpanCount=%d continuedRowProjected=%s decision=%s rejectionReason=%s recoveryAction=%s outcome=%s",
                            page_number, candidate_index,
                            "+".join(diagnostic.get("detection_strategies", ())), diagnostic["row_count"],
                            diagnostic.get("reconstructed_row_count", diagnostic["row_count"]),
                            diagnostic["column_count"], diagnostic["expected_cell_count"], len(projected),
                            diagnostic.get("source_span_count", 0), diagnostic.get("mapped_span_count", 0),
                            diagnostic.get("source_token_count", 0), diagnostic.get("projected_token_count", 0),
                            diagnostic.get("source_text_matches_grid", False),
                            diagnostic.get("extracted_empty_row_count", 0),
                            diagnostic.get("aligned_following_span_count", 0),
                            diagnostic.get("continued_row_projected", False), diagnostic["decision"],
                            diagnostic.get("rejection_reason") or "none",
                            "adopt_confirmed_structure" if diagnostic["decision"] == "confirmed_structured"
                            else "preserve_original_text_and_review",
                            "adopted" if diagnostic["decision"] == "confirmed_structured" else "deferred")
            table_regions = []
            for table_index, (table, strategy, projected, diagnostic) in enumerate(selected):
                if diagnostic["decision"] != "confirmed_structured":
                    continue
                table_bbox = tuple(float(value) for value in diagnostic.get(
                    "effective_bbox", getattr(table, "bbox", ())))
                if len(table_bbox) != 4:
                    continue
                blocks = _without_consumed_header_blocks(blocks, projected)
                table_id = f"p{page_number}-table-{table_index}"
                table_regions.append(table_bbox)
                for cell in projected:
                    blocks.append({**cell, "block_id": f"{table_id}-r{cell['row']}-c{cell['column']}",
                                   "table_id": table_id})
            if table_regions:
                blocks = [block for block in blocks if block.get("kind") == "table_cell" or not _inside_table_region(block.get("bbox"), table_regions)]
            page_value = {"page_number": page_number, "geometry": {"width": page.rect.width, "height": page.rect.height}, "blocks": blocks,
                          "page_classification": page_classification,
                          "form_diagnostics": form_diagnostics,
                          "native_table_diagnostics": table_diagnostics}
            if page_number == 1:
                try:
                    outline = document.get_toc()
                except Exception as exc:
                    _layout_log("dev_document_references stage=bookmark_extraction failureType=%s route=continue_without_bookmarks recoveryAction=use_printed_contents_only outcome=failed",
                                 type(exc).__name__)
                    outline = ()
                page_value["pdf_bookmarks"] = tuple(
                    {"level": int(level), "title": str(title), "target_page": int(target_page)}
                    for level, title, target_page in outline
                )
            yield page_value


def _provider_extractor() -> Extractor:
    provider = os.getenv("PREPROCESSING_PDF_PROVIDER", "hybrid").strip().casefold()
    if provider == "hybrid":
        from preprocessing_agent.adapters.hybrid_pdf import HybridPdfAdapter
        _layout_log("dev_preprocessing_pdf_provider stage=selected expectedProvider=pymupdf_and_docling actualProvider=hybrid route=parser_extractor outcome=selected")
        return HybridPdfAdapter().extract
    if provider == "pymupdf4llm":
        from preprocessing_agent.adapters.pymupdf4llm_pdf import PyMuPdf4LlmPdfAdapter
        _layout_log("dev_preprocessing_pdf_provider stage=selected expectedProvider=pymupdf4llm actualProvider=pymupdf4llm route=parser_extractor outcome=selected")
        return PyMuPdf4LlmPdfAdapter().extract
    if provider == "pymupdf":
        _layout_log("dev_preprocessing_pdf_provider stage=selected expectedProvider=pymupdf4llm actualProvider=pymupdf_legacy route=parser_extractor outcome=selected")
        return _default_extractor
    if provider == "docling":
        from preprocessing_agent.adapters.docling_pdf import DoclingPdfAdapter
        _layout_log("dev_preprocessing_pdf_provider stage=selected expectedProvider=pymupdf_or_docling actualProvider=docling route=parser_extractor outcome=selected")
        return DoclingPdfAdapter().extract
    raise ParserError(f"unsupported PREPROCESSING_PDF_PROVIDER: {provider}")


def _find_native_tables(page: Any, page_number: int) -> list[tuple[Any, str]]:
    """Collect candidates from native line and text detection for reconstruction."""
    import contextlib
    import sys

    found: list[tuple[Any, str]] = []
    for strategy in ("lines", "text"):
        started = time.monotonic()
        try:
            with contextlib.redirect_stdout(sys.stderr):
                finder = page.find_tables() if strategy == "lines" else page.find_tables(strategy="text")
            candidates = tuple(getattr(finder, "tables", finder or ()))
        except (AttributeError, RuntimeError, TypeError) as exc:
            _layout_log("dev_preprocessing_layout stage=native_table_detection pageNumber=%d strategy=%s candidateCount=0 elapsedMs=%d failureType=%s recoveryAction=try_next_strategy outcome=failed",
                         page_number, strategy, round((time.monotonic() - started) * 1000), type(exc).__name__)
            continue
        _layout_log("dev_preprocessing_layout stage=native_table_detection pageNumber=%d strategy=%s candidateCount=%d elapsedMs=%d failureType=none recoveryAction=compare_candidates outcome=%s",
                     page_number, strategy, len(candidates), round((time.monotonic() - started) * 1000),
                     "candidates_found" if candidates else "no_candidates")
        found.extend((candidate, strategy) for candidate in candidates)
    return found


def _native_bbox(table: Any) -> tuple[float, float, float, float]:
    bbox = getattr(table, "bbox", ())
    return tuple(float(value) for value in bbox) if isinstance(bbox, (tuple, list)) and len(bbox) == 4 else ()


def _same_native_table_region(left: tuple[float, ...], right: tuple[float, ...]) -> bool:
    if len(left) != 4 or len(right) != 4:
        return False
    intersection = max(0.0, min(left[2], right[2]) - max(left[0], right[0])) * max(
        0.0, min(left[3], right[3]) - max(left[1], right[1]))
    left_area = max(0.0, left[2] - left[0]) * max(0.0, left[3] - left[1])
    right_area = max(0.0, right[2] - right[0]) * max(0.0, right[3] - right[1])
    union = left_area + right_area - intersection
    return union > 0 and intersection / union >= 0.9


def _reconcile_native_table_candidates(candidates: list[tuple[Any, str]],
                                       reconstructed: list[tuple[list[dict[str, Any]], dict[str, Any]]],
                                       page_number: int) -> tuple[list[tuple[Any, str, list[dict[str, Any]], dict[str, Any]]], list[dict[str, Any]]]:
    """Deduplicate near-equal regions after reconstruction outcomes are known."""
    selected: list[tuple[Any, str, list[dict[str, Any]], dict[str, Any]]] = []
    diagnostics: list[dict[str, Any]] = []
    for index, ((table, strategy), (cells, diagnostic)) in enumerate(zip(candidates, reconstructed)):
        diagnostics.append(diagnostic)
        duplicate = next((position for position, (prior, _, _, _) in enumerate(selected)
                          if _same_native_table_region(_native_bbox(table), _native_bbox(prior))), None)
        if duplicate is None:
            selected.append((table, strategy, cells, diagnostic))
            continue
        prior_table, prior_strategy, prior_cells, prior_diagnostic = selected[duplicate]
        rank = {"confirmed_structured": 2, "strong_unresolved": 1, "weak_candidate": 0}
        quality = lambda item: (rank.get(item["decision"], -1),
                                bool(item.get("source_text_matches_grid", False)),
                                item.get("mapped_span_count", 0) / max(1, item.get("source_span_count", 0)),
                                item["observed_cell_geometry_count"] / max(1, item["expected_cell_count"]))
        if quality(diagnostic) > quality(prior_diagnostic):
            loser = prior_diagnostic
            strategies = "+".join(dict.fromkeys((*prior_strategy.split("+"), strategy)))
            selected[duplicate] = (table, strategies, cells, diagnostic)
        else:
            loser = diagnostic
            strategies = "+".join(dict.fromkeys((*prior_strategy.split("+"), strategy)))
            selected[duplicate] = (prior_table, strategies, prior_cells, prior_diagnostic)
            prior_diagnostic.update(detection_strategies=strategies, detection_deduplicated=True)
        loser.update(candidate_kind="overlapping_detection_duplicate",
                     reconstruction_decision=loser.get("decision"),
                     reconstruction_rejection_reason=loser.get("rejection_reason"),
                     decision="weak_candidate", rejection_reason="NEAR_IDENTICAL_REGION_RECONCILED",
                     deduplicated_into_candidate_index=duplicate)
        _layout_log("dev_preprocessing_layout stage=native_table_candidate pageNumber=%d candidateIndex=%d priorCandidateIndex=%d priorStrategy=%s candidateStrategy=%s retainedDecision=%s recoveryAction=keep_best_reconstruction_and_preserve_diagnostic outcome=overlap_deduplicated",
                    page_number, index, duplicate, prior_strategy, strategy,
                    selected[duplicate][3]["decision"])
    for table, strategy, _, diagnostic in selected:
        diagnostic.update(detection_strategies=strategy.split("+"), detection_deduplicated="+" in strategy)
    return selected, diagnostics


def _classify_text_detection_hypothesis(diagnostic: dict[str, Any], strategy: str,
                                        page_number: int = 0) -> None:
    """Keep failed text-only detections visible without treating them as table evidence."""
    if strategy == "text" and diagnostic.get("unsupported_empty_row_count", 0):
        diagnostic.update(decision="weak_candidate", candidate_kind="unconfirmed_text_grid",
                          reconstruction_decision="strong_unresolved",
                          reconstruction_rejection_reason="TEXT_GRID_HAS_UNSUPPORTED_EMPTY_ROWS",
                          rejection_reason="TEXT_DETECTION_EMPTY_ROWS_REQUIRE_GEOMETRIC_CONFIRMATION",
                          signals=[*diagnostic["signals"], "TEXT_GRID_HAS_UNSUPPORTED_EMPTY_ROWS"])
        _layout_log("dev_preprocessing_layout stage=native_table_candidate pageNumber=%d strategy=%s expectedEmptyRowCount=0 actualEmptyRowCount=%d decision=%s rejectionReason=%s recoveryAction=preserve_source_blocks_and_defer_text_grid outcome=deferred",
                    page_number, strategy, diagnostic["unsupported_empty_row_count"],
                    diagnostic["decision"], diagnostic["rejection_reason"])
        return
    if strategy == "lines" and diagnostic.get("aligned_following_span_count", 0) \
            and not diagnostic.get("continued_row_projected", False):
        diagnostic.update(decision="strong_unresolved", rejection_reason="POSSIBLE_TABLE_ROW_OUTSIDE_CANDIDATE",
                          signals=[*diagnostic["signals"], "ALIGNED_TEXT_BELOW_TABLE_BOUNDS"])
        _layout_log("dev_preprocessing_layout stage=native_table_candidate pageNumber=%d strategy=%s expectedAlignedFollowingSpanCount=0 actualAlignedFollowingSpanCount=%d decision=%s rejectionReason=%s recoveryAction=preserve_source_blocks_and_defer_partial_grid outcome=deferred",
                    page_number, strategy, diagnostic["aligned_following_span_count"],
                    diagnostic["decision"], diagnostic["rejection_reason"])
        return
    if strategy == "text" and diagnostic["decision"] == "strong_unresolved":
        diagnostic.update(decision="weak_candidate", candidate_kind="unresolved_text_detection",
                          reconstruction_decision="strong_unresolved",
                          reconstruction_rejection_reason=diagnostic["rejection_reason"],
                          rejection_reason="TEXT_DETECTION_NOT_CONFIRMED",
                          signals=[*diagnostic["signals"], "TEXT_DETECTION_RECONSTRUCTION_UNRESOLVED"])


def _inside_table_region(bbox: object, regions: list[tuple[float, float, float, float]]) -> bool:
    if not isinstance(bbox, (tuple, list)) or len(bbox) != 4:
        return False
    center_x = (float(bbox[0]) + float(bbox[2])) / 2
    center_y = (float(bbox[1]) + float(bbox[3])) / 2
    return any(x0 <= center_x <= x1 and y0 <= center_y <= y1 for x0, y0, x1, y1 in regions)


def _is_reliable_native_table(table: Any) -> bool:
    """Recognize repeated grid evidence without requiring every cell box."""
    rows = tuple(getattr(table, "rows", ()) or ())
    if len(rows) < 2:
        return False
    row_cells = tuple(tuple(getattr(row, "cells", row if isinstance(row, (list, tuple)) else ()) or ()) for row in rows)
    column_count = max((len(cells) for cells in row_cells), default=0)
    valid = [(row_index, cell) for row_index, cells in enumerate(row_cells) for cell in cells
             if isinstance(cell, (list, tuple)) and len(cell) == 4
             and all(isinstance(value, (int, float)) and math.isfinite(value) for value in cell)
             and cell[0] < cell[2] and cell[1] < cell[3]]
    row_centers = {round((float(box[1]) + float(box[3])) / 2, 3) for _, box in valid}
    edge_rows: dict[float, set[int]] = {}
    for row_index, box in valid:
        for edge in (box[0], box[2]):
            edge_rows.setdefault(round(float(edge), 1), set()).add(row_index)
    repeated_edges = sum(len(support) >= 2 for support in edge_rows.values())
    return column_count >= 2 and len(row_centers) >= 2 and len(valid) >= 2 and repeated_edges >= 2


def _reconstruct_native_table(table: Any, source_blocks: list[dict[str, Any]] | tuple[Mapping[str, Any], ...],
                              page_number: int = 0) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    """Project native cells only when geometry and candidate-region text agree."""
    reconstruction_started = time.monotonic()
    diagnostic = _native_table_candidate_diagnostic(table)
    if diagnostic["decision"] == "weak_candidate" and (
        not diagnostic["bbox"] or diagnostic["row_count"] < 2 or diagnostic["column_count"] < 2
    ):
        return [], diagnostic
    if diagnostic["decision"] == "weak_candidate":
        diagnostic.update(decision="strong_unresolved", rejection_reason="UNCONFIRMED_GRID")
    rows = tuple(getattr(table, "rows", ()) or ())
    row_cells = tuple(tuple(getattr(row, "cells", row if isinstance(row, (list, tuple)) else ()) or ()) for row in rows)
    table_bbox = diagnostic.get("bbox")
    row_heights = [float(box[3]) - float(box[1]) for cells in row_cells for box in cells
                   if isinstance(box, (list, tuple)) and len(box) == 4 and float(box[3]) > float(box[1])]
    span_heights = [float(span["bbox"][3]) - float(span["bbox"][1]) for block in source_blocks
                    for span in block.get("spans", ()) if isinstance(span, Mapping)
                    and isinstance(span.get("bbox"), (list, tuple)) and len(span["bbox"]) == 4
                    and float(span["bbox"][3]) > float(span["bbox"][1])]
    edges = sorted({round(float(edge), 1) for cells in row_cells for box in cells
                    if isinstance(box, (list, tuple)) and len(box) == 4
                    for edge in (box[0], box[2])})
    bbox_height = float(table_bbox[3]) - float(table_bbox[1]) if table_bbox else 0
    geometry = (_median_positive(span_heights) or _median_positive(row_heights)
                or (bbox_height / len(rows) if bbox_height > 0 and rows else None))
    edge_spacing = _median_positive([right - left for left, right in zip(edges, edges[1:])])
    edge_tolerance = min(geometry * 0.11, edge_spacing * 0.02) if geometry and edge_spacing else geometry * 0.11 if geometry else 0.0
    diagnostic["mapping_tolerance"] = edge_tolerance
    if len(edges) < 3:
        diagnostic.update(decision="strong_unresolved", rejection_reason="GRID_RECONSTRUCTION_AMBIGUOUS")
        return [], diagnostic
    try:
        extracted_rows = tuple(table.extract())
    except (AttributeError, RuntimeError, TypeError):
        extracted_rows = ()
    extracted_empty_row_count = sum(
        not any(str(value or "").strip() for value in row)
        for row in extracted_rows
    )
    result: list[dict[str, Any]] = []
    for row_index, cells in enumerate(row_cells):
        occupied: set[int] = set()
        for column_index, box in enumerate(cells):
            if box is None:
                continue
            if not isinstance(box, (list, tuple)) or len(box) != 4:
                diagnostic.update(decision="strong_unresolved", rejection_reason="GRID_RECONSTRUCTION_AMBIGUOUS")
                return [], diagnostic
            x0, y0, x1, y1 = map(float, box)
            starts = [index for index, edge in enumerate(edges[:-1]) if abs(edge - x0) <= edge_tolerance]
            ends = [index + 1 for index, edge in enumerate(edges[1:]) if abs(edge - x1) <= edge_tolerance]
            if len(starts) != 1 or len(ends) != 1 or ends[0] <= starts[0]:
                diagnostic.update(decision="strong_unresolved", rejection_reason="GRID_RECONSTRUCTION_AMBIGUOUS")
                return [], diagnostic
            start, end = starts[0], ends[0]
            slots = set(range(start, end))
            if start != column_index or occupied & slots:
                diagnostic.update(decision="strong_unresolved", rejection_reason="GRID_RECONSTRUCTION_AMBIGUOUS")
                return [], diagnostic
            occupied.update(slots)
            extracted = extracted_rows[row_index] if row_index < len(extracted_rows) else ()
            text = str(extracted[column_index]).strip() if column_index < len(extracted) and extracted[column_index] is not None else ""
            region_spans = [span for block in source_blocks
                            for span in block.get("spans", ()) if isinstance(span, Mapping)
                            and str(span.get("text", "")).strip()
                            and isinstance(span.get("bbox"), (list, tuple)) and len(span["bbox"]) == 4
                            and table_bbox[0] <= (float(span["bbox"][0]) + float(span["bbox"][2])) / 2 <= table_bbox[2]
                            and table_bbox[1] <= (float(span["bbox"][1]) + float(span["bbox"][3])) / 2 <= table_bbox[3]
                            and y0 - edge_tolerance <= (float(span["bbox"][1]) + float(span["bbox"][3])) / 2 <= y1 + edge_tolerance]
            mapped_spans = [(span, next((index for index, (left, right) in enumerate(zip(edges, edges[1:]))
                                         if float(span["bbox"][0]) >= left - edge_tolerance and float(span["bbox"][2]) <= right + edge_tolerance), None))
                            for span in region_spans]
            split_spans = [(span, col) for span, col in mapped_spans if col is not None and start <= col < end]
            if end - start > 1 and len({col for _, col in split_spans}) > 1:
                if len(split_spans) != len(region_spans):
                    diagnostic.update(decision="strong_unresolved", rejection_reason="GRID_RECONSTRUCTION_AMBIGUOUS")
                    return [], diagnostic
                for col in range(start, end):
                    col_spans = sorted((span for span, span_col in split_spans if span_col == col),
                                       key=lambda span: (float(span["bbox"][1]), float(span["bbox"][0])))
                    result.append({"text": " ".join(str(span.get("text", "")).strip() for span in col_spans).strip() or " ",
                                   "bbox": (edges[col], y0, edges[col + 1], y1), "kind": "table_cell",
                                   "row": row_index, "column": col, "column_span": 1,
                                   "row_span": 1, "merged": False})
            else:
                cell_spans = sorted((span for span, col in mapped_spans if col is not None and start <= col < end),
                                    key=lambda span: (float(span["bbox"][1]), float(span["bbox"][0])))
                cell_text = " ".join(str(span.get("text", "")).strip() for span in cell_spans).strip() if cell_spans else text
                result.append({"text": cell_text or " ", "bbox": tuple(box), "kind": "table_cell",
                               "row": row_index, "column": start, "column_span": end - start,
                               "row_span": 1, "merged": end - start > 1})
        if occupied != set(range(len(cells))):
            diagnostic.update(decision="strong_unresolved", rejection_reason="GRID_RECONSTRUCTION_AMBIGUOUS")
            return [], diagnostic
    bbox = diagnostic.get("bbox")
    candidate_spans = [span for block in source_blocks for span in block.get("spans", ())
                       if bbox and isinstance(span, Mapping) and str(span.get("text", "")).strip()
                       and isinstance(span.get("bbox"), (list, tuple))
                       and len(span["bbox"]) == 4
                       and bbox[0] <= (float(span["bbox"][0]) + float(span["bbox"][2])) / 2 <= bbox[2]
                       and bbox[1] <= (float(span["bbox"][1]) + float(span["bbox"][3])) / 2 <= bbox[3]]
    aligned_following_spans = _aligned_spans_below_candidate(
        table_bbox, row_cells, source_blocks, candidate_spans
    )
    continued_row_projected = False
    if aligned_following_spans and edges:
        last_row = len(rows)
        grouped: dict[int, list[Mapping[str, Any]]] = {}
        for span in aligned_following_spans:
            span_box = _bbox(span.get("bbox"))
            center_x = (span_box[0] + span_box[2]) / 2
            column = next((index for index, (left, right) in enumerate(zip(edges, edges[1:]))
                           if left - edge_tolerance <= center_x <= right + edge_tolerance), None)
            if column is None:
                grouped = {}
                break
            grouped.setdefault(column, []).append(span)
        if grouped and set(grouped) == set(range(len(edges) - 1)):
            for column, (left, right) in enumerate(zip(edges, edges[1:])):
                spans = sorted(grouped[column], key=lambda span: _bbox(span.get("bbox"))[0])
                boxes = [_bbox(span.get("bbox")) for span in spans]
                result.append({"text": " ".join(str(span.get("text", "")).strip() for span in spans).strip() or " ",
                               "bbox": (left, min(box[1] for box in boxes), right, max(box[3] for box in boxes)),
                               "kind": "table_cell", "row": last_row, "column": column,
                               "column_span": 1, "row_span": 1, "merged": False})
            candidate_spans.extend(aligned_following_spans)
            continued_row_projected = True
            diagnostic["effective_bbox"] = [float(table_bbox[0]), float(table_bbox[1]),
                                            float(table_bbox[2]),
                                            max(float(table_bbox[3]), max(_bbox(span.get("bbox"))[3]
                                                                          for span in aligned_following_spans))]
            _layout_log("dev_preprocessing_layout stage=native_table_continuation pageNumber=%d expectedColumnCount=%d actualProjectedColumnCount=%d expectedRowCount=%d actualRowCount=%d sourceSpanCount=%d recoveryAction=include_aligned_source_row_and_expand_table_region outcome=projected elapsedMs=%d",
                        page_number, len(edges) - 1, len(grouped), len(rows), last_row + 1,
                        len(aligned_following_spans), round((time.monotonic() - reconstruction_started) * 1000))
    diagnostic.update(extracted_empty_row_count=extracted_empty_row_count,
                      unsupported_empty_row_count=extracted_empty_row_count,
                      aligned_following_span_count=len(aligned_following_spans),
                      continued_row_projected=continued_row_projected,
                      reconstructed_row_count=len(rows) + int(continued_row_projected))
    source_text = " ".join(str(span.get("text", "")) for span in candidate_spans)
    if not source_text:
        source_text = " ".join(str(block.get("text", "")) for block in sorted(
        (block for block in source_blocks if _inside_table_region(block.get("bbox"), [tuple(bbox)])),
        key=lambda block: (_bbox(block.get("bbox"))[1], _bbox(block.get("bbox"))[0]))) if bbox else ""
    table_text = " ".join(str(cell["text"]) for cell in result if cell["text"].strip())
    for span in candidate_spans:
        span_box = _bbox(span.get("bbox"))
        center_x, center_y = (span_box[0] + span_box[2]) / 2, (span_box[1] + span_box[3]) / 2
        if sum(cell["bbox"][0] - edge_tolerance <= center_x <= cell["bbox"][2] + edge_tolerance
               and cell["bbox"][1] - edge_tolerance <= center_y <= cell["bbox"][3] + edge_tolerance for cell in result) != 1:
            diagnostic.update(decision="strong_unresolved", rejection_reason="GRID_RECONSTRUCTION_AMBIGUOUS")
            return [], diagnostic
    if not candidate_spans:
        table_text = " ".join(str(value) for row in extracted_rows for value in row if value)
    tokenize = lambda text: Counter(re.findall(r"\w+|[^\w\s]", text, flags=re.UNICODE))
    source_tokens, table_tokens = tokenize(source_text), tokenize(table_text)
    diagnostic.update(source_span_count=len(candidate_spans),
                      mapped_span_count=len(candidate_spans),
                      source_token_count=sum(source_tokens.values()),
                      projected_token_count=sum(table_tokens.values()),
                      source_text_matches_grid=bool(source_text) and source_tokens == table_tokens)
    if not source_text or source_tokens != table_tokens:
        diagnostic.update(decision="strong_unresolved", rejection_reason="TABLE_TEXT_MISMATCH")
        return [], diagnostic
    headers, header_error = _native_table_header(rows, edges, result, source_blocks, edge_tolerance)
    if header_error:
        diagnostic.update(decision="strong_unresolved", rejection_reason=header_error)
        return [], diagnostic
    if headers:
        for cell in result:
            cell["row"] += 1
        result = headers + result
    for cell in result:
        cell["is_header"] = bool(headers) and cell["row"] == 0
    diagnostic.update(decision="confirmed_structured", rejection_reason=None)
    return result, diagnostic


def _aligned_spans_below_candidate(table_bbox: Any, rows: tuple[Any, ...],
                                   source_blocks: list[dict[str, Any]] | tuple[Mapping[str, Any], ...],
                                   candidate_spans: list[Mapping[str, Any]]) -> list[Mapping[str, Any]]:
    """Find a nearby text row outside a detected table's bottom edge.

    This is a completeness warning only. It never adds text to a table; the
    caller must defer the candidate so the original text remains available.
    """
    if not isinstance(table_bbox, (tuple, list)) or len(table_bbox) != 4 or not rows:
        return []
    row_boxes = [box for row in rows
                 for box in (getattr(row, "cells", row if isinstance(row, (list, tuple)) else ()) or ())
                 if isinstance(box, (tuple, list)) and len(box) == 4 and box[1] < box[3]]
    centers = sorted({(float(box[1]) + float(box[3])) / 2 for box in row_boxes})
    pitches = [right - left for left, right in zip(centers, centers[1:]) if right > left]
    row_pitch = _median_positive(pitches)
    span_heights = [float(span["bbox"][3]) - float(span["bbox"][1]) for span in candidate_spans
                    if isinstance(span.get("bbox"), (tuple, list)) and len(span["bbox"]) == 4
                    and float(span["bbox"][3]) > float(span["bbox"][1])]
    span_height = _median_positive(span_heights)
    if not row_pitch or not span_height:
        return []
    left_edges = sorted({float(box[0]) for box in row_boxes})
    right_edges = sorted({float(box[2]) for box in row_boxes})
    columns = [(left, right) for left, right in zip(left_edges, right_edges) if right > left]
    if len(columns) < 2:
        return []
    tolerance = span_height * 0.6
    max_gap = row_pitch * 1.5
    excluded = {id(span) for span in candidate_spans}
    nearby: list[tuple[Mapping[str, Any], float, int]] = []
    for block in source_blocks:
        for span in block.get("spans", ()):
            box = span.get("bbox") if isinstance(span, Mapping) else None
            if id(span) in excluded or not isinstance(box, (tuple, list)) or len(box) != 4:
                continue
            x0, y0, x1, y1 = map(float, box)
            center_x, center_y = (x0 + x1) / 2, (y0 + y1) / 2
            gap = center_y - float(table_bbox[3])
            if 0 < gap <= max_gap and x0 >= float(table_bbox[0]) and x1 <= float(table_bbox[2]):
                column = next((index for index, (left, right) in enumerate(columns)
                               if left - tolerance <= center_x <= right + tolerance), None)
                if column is not None:
                    nearby.append((span, center_y, column))
    for _, center_y, _ in nearby:
        same_row = [column for _, y, column in nearby if abs(y - center_y) <= tolerance]
        if len(set(same_row)) >= min(2, len(columns)):
            return [span for span, y, _ in nearby if abs(y - center_y) <= tolerance]
    return []


def _median_positive(values: list[float]) -> float | None:
    positive = sorted(value for value in values if math.isfinite(value) and value > 0)
    return positive[len(positive) // 2] if positive else None


def _native_table_header(rows: tuple[Any, ...], edges: list[float], cells: list[dict[str, Any]],
                         source_blocks: list[dict[str, Any]] | tuple[Mapping[str, Any], ...],
                         geometry_tolerance: float) -> tuple[list[dict[str, Any]], str | None]:
    first_row = [cell for cell in cells if cell["row"] == 0]
    top = min(cell["bbox"][1] for cell in first_row)
    heights = [float(cell[3]) - float(cell[1]) for box in
               (getattr(rows[0], "cells", rows[0] if isinstance(rows[0], (list, tuple)) else ()) or ())
               if isinstance(box, (list, tuple)) and len(box) == 4 for cell in [box]]
    span_heights = [float(span["bbox"][3]) - float(span["bbox"][1]) for block in source_blocks
                    for span in block.get("spans", ()) if isinstance(span, Mapping)
                    and isinstance(span.get("bbox"), (list, tuple)) and len(span["bbox"]) == 4
                    and float(span["bbox"][3]) > float(span["bbox"][1])]
    text_height = _median_positive(span_heights) or geometry_tolerance / 0.11
    row_height = _median_positive(heights)
    max_gap = max(row_height or 0, text_height or 0) * 1.5
    candidates = []
    for block in source_blocks:
        for span_index, span in enumerate(block.get("spans", ())):
            box = span.get("bbox") if isinstance(span, Mapping) else None
            if not isinstance(box, (list, tuple)) or len(box) != 4:
                continue
            x0, y0, x1, y1 = map(float, box)
            gap = top - y1
            column = next((index for index, (left, right) in enumerate(zip(edges, edges[1:]))
                           if x0 >= left - geometry_tolerance and x1 <= right + geometry_tolerance), None)
            if 0 <= gap <= max_gap and column is not None and str(span.get("text", "")).strip():
                candidates.append((span, column, (y0 + y1) / 2, gap, (id(block), span_index)))
    candidates.sort(key=lambda item: item[2])
    groups: list[list[tuple[Mapping[str, Any], int, float, float]]] = []
    for candidate in candidates:
        if not groups or candidate[2] - groups[-1][-1][2] > text_height * 0.3:
            groups.append([candidate])
        else:
            groups[-1].append(candidate)
    aligned = [group for group in groups if len({item[1] for item in group}) >= 2]
    if not aligned:
        return [], None
    aligned.sort(key=lambda group: min(item[3] for item in group))
    closest_y = sum(item[2] for item in aligned[0]) / len(aligned[0])
    selected = [item for group in aligned
                if abs(sum(item[2] for item in group) / len(group) - closest_y) <= text_height * 1.5
                for item in group]
    for index, (left, left_column, _, _, _) in enumerate(selected):
        left_box = left["bbox"]
        for right, right_column, _, _, _ in selected[index + 1:]:
            right_box = right["bbox"]
            if (left_column == right_column and min(float(left_box[2]), float(right_box[2])) > max(float(left_box[0]), float(right_box[0]))
                    and min(float(left_box[3]), float(right_box[3])) > max(float(left_box[1]), float(right_box[1]))
                    and str(left.get("text", "")).strip() != str(right.get("text", "")).strip()):
                return [], "CONFLICTING_HEADER_EVIDENCE"
    headers = []
    header_top = min(float(item[0]["bbox"][1]) for item in selected)
    header_bottom = max(float(item[0]["bbox"][3]) for item in selected)
    for column in range(len(edges) - 1):
        spans = sorted((item[0] for item in selected if item[1] == column),
                       key=lambda span: (float(span["bbox"][1]), float(span["bbox"][0])))
        refs = [item[4] for item in selected if item[1] == column]
        headers.append({"text": " ".join(str(span.get("text", "")).strip() for span in spans).strip() or " ",
                        "bbox": ((min(float(span["bbox"][0]) for span in spans) if spans else edges[column]),
                                 (min(float(span["bbox"][1]) for span in spans) if spans else header_top),
                                 (max(float(span["bbox"][2]) for span in spans) if spans else edges[column + 1]),
                                 (max(float(span["bbox"][3]) for span in spans) if spans else header_bottom)),
                        "kind": "table_cell", "row": 0, "column": column,
                        "column_span": 1, "row_span": 1, "merged": False, "is_header": True,
                        "source_span_refs": refs})
    return headers, None


def _without_consumed_header_blocks(blocks: list[dict[str, Any]], cells: list[dict[str, Any]]) -> list[dict[str, Any]]:
    consumed = {tuple(ref) for cell in cells for ref in cell.pop("source_span_refs", ())}
    result = []
    for block_index, block in enumerate(blocks):
        text_spans = [(span_index, span) for span_index, span in enumerate(block.get("spans", ()))
                      if str(span.get("text", "")).strip()]
        if text_spans and all((id(block), span_index) in consumed for span_index, _ in text_spans):
            continue
        result.append(block)
    return result


def _native_table_candidate_diagnostic(table: Any) -> dict[str, Any]:
    rows = tuple(getattr(table, "rows", ()) or ())
    row_cells = tuple(tuple(getattr(row, "cells", row if isinstance(row, (list, tuple)) else ()) or ()) for row in rows)
    column_count = max((len(cells) for cells in row_cells), default=0)
    expected = sum(len(cells) for cells in row_cells)
    valid_boxes = sum(1 for cells in row_cells for cell in cells
                      if isinstance(cell, (list, tuple)) and len(cell) == 4
                      and all(isinstance(value, (int, float)) and math.isfinite(value) for value in cell)
                      and cell[0] <= cell[2] and cell[1] <= cell[3])
    missing = expected - valid_boxes
    raw_bbox = getattr(table, "bbox", None)
    bbox_valid = (isinstance(raw_bbox, (list, tuple)) and len(raw_bbox) == 4
                  and all(isinstance(value, (int, float)) and math.isfinite(value) for value in raw_bbox)
                  and raw_bbox[0] <= raw_bbox[2] and raw_bbox[1] <= raw_bbox[3])
    widths = {len(cells) for cells in row_cells}
    rectangular = bool(widths) and len(widths) == 1 and column_count >= 2
    coverage = valid_boxes / expected if expected else 0.0
    row_centers: set[float] = set()
    x_edge_rows: dict[float, set[int]] = {}
    for row_index, cells in enumerate(row_cells):
        boxes = [cell for cell in cells if isinstance(cell, (list, tuple)) and len(cell) == 4
                 and all(isinstance(value, (int, float)) and math.isfinite(value) for value in cell)
                 and cell[0] <= cell[2] and cell[1] <= cell[3]]
        if boxes:
            row_centers.add(round((min(box[1] for box in boxes) + max(box[3] for box in boxes)) / 2, 1))
        for box in boxes:
            for edge in (box[0], box[2]):
                x_edge_rows.setdefault(round(float(edge), 1), set()).add(row_index)
    repeated_x_edge_count = sum(len(rows_with_edge) >= 2 for rows_with_edge in x_edge_rows.values())
    repeated_row_geometry = len(row_centers) >= 3
    repeated_column_geometry = repeated_x_edge_count >= min(column_count + 1, 3)
    signals = [
        "VALID_TABLE_BBOX" if bbox_valid else "INVALID_TABLE_BBOX",
        f"ROW_COUNT_{len(rows)}", f"COLUMN_COUNT_{column_count}",
        f"CELL_GEOMETRY_COVERAGE_{coverage:.3f}",
        f"DISTINCT_ROW_CENTER_COUNT_{len(row_centers)}",
        f"X_EDGE_SUPPORT_ACROSS_ROWS_COUNT_{repeated_x_edge_count}",
        "RECTANGULAR_GRID" if rectangular else "IRREGULAR_GRID",
    ]
    if missing:
        signals.append("MISSING_CELL_BBOXES")
    if _is_reliable_native_table(table) and missing == 0 and rectangular:
        decision, reason = "confirmed_structured", None
    elif (bbox_valid and len(rows) >= 3 and column_count >= 2 and coverage >= .25
          and repeated_row_geometry and repeated_column_geometry):
        decision, reason = "strong_unresolved", "MISSING_CELL_BBOXES" if missing else "UNCONFIRMED_GRID"
    else:
        decision, reason = "weak_candidate", (
            "INVALID_TABLE_BBOX" if not bbox_valid else "INSUFFICIENT_GEOMETRIC_EVIDENCE"
        )
    return {"bbox": list(raw_bbox) if bbox_valid else None, "row_count": len(rows),
            "column_count": column_count, "missing_cell_count": missing,
            "observed_cell_geometry_count": valid_boxes, "expected_cell_count": expected,
            "signals": signals, "decision": decision, "rejection_reason": reason}
