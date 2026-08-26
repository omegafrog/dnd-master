"""PDF extraction adapter with an injectable extraction seam.

The optional PDF libraries are deliberately kept at the adapter boundary. Tests
and callers may provide an extractor returning page/block mappings without
installing a particular PDF implementation.
"""

from __future__ import annotations

import hashlib
from pathlib import Path
from typing import Any, Callable, Iterable, Mapping

from preprocessing_agent.domain import ParsedBlock, ParsedDocument, ParsedPage, SourceSpan
from preprocessing_agent.layout import ReadingOrderPlanner
from preprocessing_agent.domain.serialization import to_dict
from .base import ParserError
from .normalize import normalize_text


Extractor = Callable[[Path], Iterable[Mapping[str, Any]]]


class PdfDocumentParser:
    def __init__(self, extractor: Extractor | None = None) -> None:
        self._extractor = extractor or _default_extractor

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
            page_number = int(raw_page.get("page_number", page_position))
            # Keep source identity stable across geometric projection. The
            # ordinal is provenance, not a reading-order signal.
            source_blocks = tuple(
                ({**raw_block, "block_id": str(raw_block.get("block_id", f"p{page_number}-b{index}"))})
                for index, raw_block in enumerate(raw_page.get("blocks", ()))
            )
            missing_geometry = any(raw_block.get("bbox") is None for raw_block in source_blocks)
            plan = None if missing_geometry else ReadingOrderPlanner().plan(source_blocks)
            if missing_geometry:
                # Text-only callers remain compatible, but the missing
                # geometry is explicit evidence and cannot be READY-published.
                raw_blocks = source_blocks
                layout_diagnostics.append({"page_number": page_number, "finding": "LAYOUT_GEOMETRY_REQUIRED"})
            elif plan.ambiguous:
                # Use the planner's deterministic geometric diagnostic order,
                # never the extractor/source order. The application gate still
                # blocks this page from READY publication.
                by_id = {str(block["block_id"]): block for block in source_blocks}
                raw_blocks = tuple(by_id[block_id] for block_id in plan.ordered_block_ids if block_id in by_id)
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
                span = SourceSpan(page_number, block_position, char_cursor, char_cursor + len(text))
                block = ParsedBlock(
                    block_id=block_id,
                    source_text=text,
                    source_span=span,
                    bbox=_bbox(raw_block.get("bbox")),
                    font_size=_number(raw_block.get("font_size", raw_block.get("size"))),
                    font_weight=_font_weight(raw_block),
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
            pages.append(ParsedPage(page_number, tuple(blocks), page_text, headings, tables))
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
            for table_index, table in enumerate(found_tables):
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
