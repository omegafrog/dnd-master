"""PDF extraction adapter with an injectable extraction seam.

The optional PDF libraries are deliberately kept at the adapter boundary. Tests
and callers may provide an extractor returning page/block mappings without
installing a particular PDF implementation.
"""

from __future__ import annotations

import hashlib
import json
import logging
import math
import os
import time
from pathlib import Path
from typing import Any, Callable, Iterable, Mapping

from preprocessing_agent.domain import ParsedBlock, ParsedDocument, ParsedPage, SourceSpan, ReadingOrderPlan, from_dict, schema_path, validate_json
from preprocessing_agent.layout import ReadingOrderPlanner
from preprocessing_agent.domain.serialization import to_dict
from .base import ParserError
from .normalize import normalize_text


Extractor = Callable[[Path], Iterable[Mapping[str, Any]]]
_LOGGER = logging.getLogger(__name__)


class PdfDocumentParser:
    def __init__(self, extractor: Extractor | None = None, layout_selections: dict[int, dict[str, int]] | None = None) -> None:
        self._extractor = extractor or _default_extractor
        self._layout_selections = layout_selections or {}

    def parse(self, source: Path) -> ParsedDocument:
        source = Path(source)
        try:
            raw_pages = tuple(self._extractor(source))
        except Exception as exc:  # normalize adapter errors at the port boundary
            raise ParserError(f"failed to parse {source}: {exc}") from exc
        pages: list[ParsedPage] = []
        layout_diagnostics: list[dict[str, Any]] = []
        document_parts: list[str] = []
        for page_position, raw_page in enumerate(raw_pages, start=1):
            page_started = time.monotonic()
            page_number = int(raw_page.get("page_number", page_position))
            # Keep source identity stable across geometric projection. The
            # ordinal is provenance, not a reading-order signal.
            source_blocks = _deduplicate_blocks(tuple(
                ({**raw_block, "block_id": str(raw_block.get("block_id", f"p{page_number}-b{index}"))})
                for index, raw_block in enumerate(raw_page.get("blocks", ()))
            ))
            missing_geometry = any(raw_block.get("bbox") is None for raw_block in source_blocks)
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
                plan = None if missing_geometry else ReadingOrderPlanner().plan(
                    source_blocks, selections=self._layout_selections.get(page_number, {}))
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
                )
                blocks.append(block)
                page_parts.append(text)
                char_cursor += len(text) + 1
            page_text = normalize_text("\n".join(page_parts))
            from preprocessing_agent.structure import HeadingAssociator, TableStructureDetector
            blocks_by_id = {block.block_id: block for block in blocks}
            page_structure = tuple({**raw_block, "source_text": blocks_by_id[str(raw_block["block_id"])].source_text} for raw_block in raw_blocks if str(raw_block["block_id"]) in blocks_by_id)
            headings = HeadingAssociator().associate(page_structure, plan) if plan is not None else ()
            tables = TableStructureDetector().detect(page_structure) if plan is not None else ()
            pages.append(ParsedPage(page_number, tuple(blocks), page_text, headings, tables,
                                   str(raw_page.get("page_classification", "text-native")), layout_plan=plan))
            document_parts.append(page_text)
        source_text = normalize_text("\n".join(document_parts))
        document_id = hashlib.sha256(source_text.encode("utf-8")).hexdigest()[:16]
        metadata = {"format": "pdf"}
        if layout_diagnostics:
            metadata["layout_diagnostics"] = layout_diagnostics
        return ParsedDocument(document_id, str(source), source_text, tuple(pages), metadata)


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
        import fitz  # type: ignore
    except ImportError:
        raise ParserError("install PyMuPDF or provide an extractor")
    with fitz.open(source) as document:
        for page_number, page in enumerate(document, start=1):
            blocks = []
            for block in page.get_text("dict").get("blocks", []):
                lines = block.get("lines", [])
                spans = [span for line in lines for span in line.get("spans", [])]
                text = _block_text(block).strip()
                if not text:
                    continue
                first = spans[0] if spans else {}
                blocks.append({"text": text, "bbox": block.get("bbox"), "font_size": first.get("size"), "font": first.get("font")})
            # Preserve native table structure when the installed PyMuPDF
            # exposes find_tables(); unsupported versions simply yield no
            # table cells and retain the normal text extraction path.
            try:
                import contextlib
                import sys
                with contextlib.redirect_stdout(sys.stderr):
                    finder = page.find_tables()
                found_tables = getattr(finder, "tables", finder or ())
            except (AttributeError, RuntimeError, TypeError):
                found_tables = ()
            table_regions = []
            for table_index, table in enumerate(table for table in found_tables if _is_reliable_native_table(table)):
                table_id = f"p{page_number}-table-{table_index}"
                table_bbox = getattr(table, "bbox", None)
                if table_bbox is not None and len(table_bbox) == 4:
                    table_regions.append(tuple(float(value) for value in table_bbox))
                rows = getattr(table, "rows", ()) or ()
                try:
                    extracted_rows = table.extract()
                except (AttributeError, RuntimeError, TypeError):
                    extracted_rows = ()
                for row_index, row in enumerate(rows):
                    cells = getattr(row, "cells", row if isinstance(row, (list, tuple)) else ()) or ()
                    extracted = extracted_rows[row_index] if row_index < len(extracted_rows) else ()
                    for column_index, cell_bbox in enumerate(cells):
                        if cell_bbox is None or len(cell_bbox) != 4:
                            continue
                        text = str(extracted[column_index]).strip() if column_index < len(extracted) else ""
                        blocks.append({"block_id": f"{table_id}-r{row_index}-c{column_index}", "text": text or " ",
                                           "bbox": tuple(cell_bbox), "kind": "table_cell", "table_id": table_id,
                                           "row": row_index, "column": column_index, "is_header": row_index == 0})
            if table_regions:
                blocks = [block for block in blocks if block.get("kind") == "table_cell" or not _inside_table_region(block.get("bbox"), table_regions)]
            yield {"page_number": page_number, "blocks": blocks}


def _inside_table_region(bbox: object, regions: list[tuple[float, float, float, float]]) -> bool:
    if not isinstance(bbox, (tuple, list)) or len(bbox) != 4:
        return False
    center_x = (float(bbox[0]) + float(bbox[2])) / 2
    center_y = (float(bbox[1]) + float(bbox[3])) / 2
    return any(x0 <= center_x <= x1 and y0 <= center_y <= y1 for x0, y0, x1, y1 in regions)


def _is_reliable_native_table(table: Any) -> bool:
    """Accept only repeated, rectangular native grids for cell projection."""
    rows = tuple(getattr(table, "rows", ()) or ())
    if len(rows) < 2:
        return False
    row_cells = tuple(tuple(getattr(row, "cells", row if isinstance(row, (list, tuple)) else ()) or ()) for row in rows)
    widths = {len(cells) for cells in row_cells}
    if len(widths) != 1 or not widths or next(iter(widths)) < 2:
        return False
    if any(cell is None or len(cell) != 4 for cells in row_cells for cell in cells):
        return False
    row_centers = {round((float(cells[0][1]) + float(cells[0][3])) / 2, 3) for cells in row_cells}
    return len(row_centers) >= 2


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
    if _is_reliable_native_table(table):
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
