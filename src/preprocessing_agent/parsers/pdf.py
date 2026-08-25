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
        document_parts: list[str] = []
        for page_position, raw_page in enumerate(raw_pages, start=1):
            page_number = int(raw_page.get("page_number", page_position))
            raw_blocks = _order_blocks(tuple(raw_page.get("blocks", ())))
            blocks: list[ParsedBlock] = []
            page_parts: list[str] = []
            char_cursor = 0
            for block_position, raw_block in enumerate(raw_blocks):
                text = _block_text(raw_block)
                if not text:
                    continue
                # Sort only when the extractor explicitly supplies reading order;
                # otherwise its block order is authoritative and deterministic.
                block_id = str(raw_block.get("block_id", f"p{page_number}-b{block_position}"))
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
            pages.append(ParsedPage(page_number, tuple(blocks), page_text))
            document_parts.append(page_text)
        source_text = normalize_text("\n".join(document_parts))
        document_id = hashlib.sha256(source_text.encode("utf-8")).hexdigest()[:16]
        return ParsedDocument(document_id, str(source), source_text, tuple(pages), {"format": "pdf"})


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
    """Apply reading order only when the page has a clear two-column layout.

    PyMuPDF's dict order is an extraction detail rather than a reading-order
    contract.  The layout signal is intentionally conservative: every block
    must have a bbox, the two x-bands must be well separated, and the split
    must be substantially stronger than any other x gap.  Pages that do not
    meet those conditions retain the extractor's original order.
    """
    split = _strong_column_split(blocks)
    if split is None:
        return blocks

    left: list[tuple[int, Mapping[str, Any]]] = []
    right: list[tuple[int, Mapping[str, Any]]] = []
    spanning: list[tuple[int, Mapping[str, Any]]] = []
    for position, block in enumerate(blocks):
        bbox = _bbox(block.get("bbox"))
        assert bbox is not None
        if bbox[2] <= split:
            left.append((position, block))
        elif bbox[0] >= split:
            right.append((position, block))
        else:
            spanning.append((position, block))

    def reading_key(item: tuple[int, Mapping[str, Any]]) -> tuple[float, int]:
        bbox = _bbox(item[1].get("bbox"))
        assert bbox is not None
        return bbox[1], item[0]

    left.sort(key=reading_key)
    right.sort(key=reading_key)
    spanning.sort(key=reading_key)
    if spanning:
        column_blocks = left + right
        first_column_y = min(reading_key(item)[0] for item in column_blocks)
        leading = [item for item in spanning if reading_key(item)[0] <= first_column_y]
        trailing = [item for item in spanning if reading_key(item)[0] > first_column_y]
        return tuple(block for _, block in leading + column_blocks + trailing)
    return tuple(block for _, block in left + right)


def _strong_column_split(blocks: tuple[Mapping[str, Any], ...]) -> float | None:
    if len(blocks) < 4:
        return None
    bboxes = [_bbox(block.get("bbox")) for block in blocks]
    if any(bbox is None for bbox in bboxes):
        return None
    valid_bboxes = [bbox for bbox in bboxes if bbox is not None]
    x_starts = sorted({bbox[0] for bbox in valid_bboxes})
    if len(x_starts) < 2:
        return None

    page_left = min(bbox[0] for bbox in valid_bboxes)
    page_right = max(bbox[2] for bbox in valid_bboxes)
    page_width = page_right - page_left
    if page_width <= 0:
        return None
    gaps = [(x_starts[index + 1] - x_starts[index], x_starts[index], x_starts[index + 1])
            for index in range(len(x_starts) - 1)]
    gap_index, (gap, left_start, right_start) = max(enumerate(gaps), key=lambda item: item[1][0])
    if gap < page_width * 0.2:
        return None
    other_gaps = [candidate[0] for index, candidate in enumerate(gaps) if index != gap_index]
    if other_gaps and gap < max(other_gaps) * 2:
        return None

    left_blocks = [bbox for bbox in valid_bboxes if bbox[0] < right_start and bbox[2] <= right_start]
    right_blocks = [bbox for bbox in valid_bboxes if bbox[0] >= right_start]
    if len(left_blocks) < 2 or len(right_blocks) < 2:
        return None
    left_right = max(bbox[2] for bbox in left_blocks)
    right_left = min(bbox[0] for bbox in right_blocks)
    if left_right >= right_left:
        return None
    return (left_right + right_left) / 2


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
            yield {"page_number": page_number, "blocks": blocks}
