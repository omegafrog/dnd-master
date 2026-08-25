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
            raw_blocks = tuple(raw_page.get("blocks", ()))
            blocks: list[ParsedBlock] = []
            page_parts: list[str] = []
            char_cursor = 0
            for block_position, raw_block in enumerate(raw_blocks):
                text = str(raw_block.get("text", raw_block.get("source_text", "")))
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
                text = "".join(span.get("text", "") for span in spans).strip()
                if not text:
                    continue
                first = spans[0] if spans else {}
                blocks.append({"text": text, "bbox": block.get("bbox"), "font_size": first.get("size"), "font": first.get("font")})
            yield {"page_number": page_number, "blocks": blocks}
