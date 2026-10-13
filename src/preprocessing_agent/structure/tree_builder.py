"""Build an immutable ordered document tree from parsed blocks."""

from __future__ import annotations

from dataclasses import dataclass, field
import hashlib
import logging
import os
import re
import time
from collections.abc import Mapping

from preprocessing_agent.domain import ContentType, DocumentTree, ParsedDocument, SectionNode, SourceSegment, SourceSpan
from .detector import HeadingDetector
from preprocessing_agent.parsers.normalize import normalized_key

_LOGGER = logging.getLogger(__name__)


@dataclass
class _MutableNode:
    title: str
    level: int
    span: SourceSpan | None
    block_ids: list[str] = field(default_factory=list)
    children: list["_MutableNode"] = field(default_factory=list)
    source_segments: list[SourceSegment] = field(default_factory=list)
    hierarchy_resolved: bool = True


class DocumentTreeBuilder:
    def __init__(self, detector: HeadingDetector | None = None) -> None:
        self.detector = detector or HeadingDetector()

    def build(self, document: ParsedDocument) -> DocumentTree:
        started_at = time.monotonic()
        if _diagnostics_enabled():
            _LOGGER.info("dev_document_tree stage=start documentId=%s pageCount=%d blockCount=%d outcome=started", document.document_id, len(document.pages), sum(len(page.blocks) for page in document.pages))
        root = _MutableNode(document.document_id, 0, None)
        stack: list[_MutableNode] = [root]
        repeated = _repeated_furniture(document)
        repeated_skipped = page_markers_skipped = multi_heading_blocks = table_cells_preserved = 0
        unresolved_page_owned_block_count = 0
        for page in document.pages:
            unresolved_page_owner: _MutableNode | None = None
            associations: dict[str, list[object]] = {}
            for item in page.heading_associations:
                associations.setdefault(str(_value(item, "heading_block_id")), []).append(item)
            for values in associations.values():
                values.sort(key=lambda item: int(_value(item, "source_char_start", 0) or 0))
                if len(values) > 1:
                    multi_heading_blocks += 1
            associated_with: dict[str, tuple[str, int, int]] = {}
            for heading_id, heading_values in associations.items():
                for association in heading_values:
                    heading_key = _heading_key(heading_id, association)
                    for block_id in _value(association, "associated_block_ids", ()):
                        associated_with.setdefault(str(block_id), heading_key)
            nodes_by_heading: dict[tuple[str, int, int], _MutableNode] = {}
            for block in page.blocks:
                is_table_cell = block.source_span.table_id is not None or block.source_span.table_cell is not None
                if is_table_cell:
                    table_cells_preserved += 1
                elif _is_repeated_furniture_block(block, repeated):
                    repeated_skipped += 1
                    continue
                if not is_table_cell and _is_page_marker(block):
                    page_markers_skipped += 1
                    continue

                headings = associations.get(block.block_id, ())
                if headings:
                    cursor = 0
                    for index, heading in enumerate(headings):
                        start, end = _heading_range(block, heading)
                        _append_range(unresolved_page_owner or stack[-1], block, cursor, start)
                        heading_key = _heading_key(block.block_id, heading)
                        hierarchy_resolved = bool(_value(heading, "hierarchy_resolved", True))
                        level = max(1, int(_value(heading, "level", 1)))
                        if hierarchy_resolved:
                            while len(stack) > 1 and stack[-1].level >= level:
                                stack.pop()
                            parent = stack[-1]
                            unresolved_page_owner = None
                        else:
                            # Keep an uncertain title visible without inventing its parent.
                            stack[:] = [root]
                            parent = root
                        heading_text = _value(heading, "heading_text")
                        preserve_full_block = start == 0 and end == len(block.source_text)
                        heading_span = (block.source_span if preserve_full_block else
                                        _slice_span(block.source_span, start, end))
                        node = _MutableNode(str(heading_text or block.source_text[start:end].strip()), level,
                                            heading_span, [block.block_id], hierarchy_resolved=hierarchy_resolved)
                        parent.children.append(node)
                        if hierarchy_resolved:
                            stack.append(node)
                        else:
                            # Preserve local title-to-body ownership while leaving the
                            # semantic section path unresolved.
                            unresolved_page_owner = node
                        nodes_by_heading[heading_key] = node
                        next_start = (_heading_range(block, headings[index + 1])[0]
                                      if index + 1 < len(headings) else len(block.source_text))
                        _append_range(node, block, 0 if preserve_full_block else end, next_start)
                        cursor = next_start
                    continue

                analyze_structure = document.metadata.get("structure_analysis", True) is not False
                decision = (self.detector.detect(block)
                            if analyze_structure and not page.heading_associations and not page.heading_candidates
                            else None)
                if decision is not None and decision.is_heading:
                    level = max(1, int(decision.level or 1))
                    while len(stack) > 1 and stack[-1].level >= level:
                        stack.pop()
                    node = _MutableNode(block.source_text.strip(), level, block.source_span, [block.block_id])
                    stack[-1].children.append(node)
                    stack.append(node)
                    _append_range(node, block, 0, len(block.source_text))
                elif block.block_id in associated_with and associated_with[block.block_id] in nodes_by_heading:
                    target = nodes_by_heading[associated_with[block.block_id]]
                    _append_range(target, block, 0, len(block.source_text))
                else:
                    owner = unresolved_page_owner or stack[-1]
                    _append_range(owner, block, 0, len(block.source_text))
                    if unresolved_page_owner is not None:
                        unresolved_page_owned_block_count += 1
        result = DocumentTree(document.document_id, _freeze(root, document.document_id))
        if _diagnostics_enabled():
            _LOGGER.info("dev_document_tree stage=complete documentId=%s pageCount=%d associationCount=%d multiHeadingBlockCount=%d unresolvedHierarchyCount=%d unresolvedPageOwnedBlockCount=%d repeatedFurnitureSkippedCount=%d tableCellPreservedCount=%d pageMarkerSkippedCount=%d sourceSegmentCount=%d detectorFallbackPages=%d elapsedMs=%d outcome=success", document.document_id, len(document.pages), sum(len(page.heading_associations) for page in document.pages), multi_heading_blocks, sum(not _value(item, "hierarchy_resolved", True) for page in document.pages for item in page.heading_associations), unresolved_page_owned_block_count, repeated_skipped, table_cells_preserved, page_markers_skipped, sum(len(node.source_segments) for node, _ in _walk_mutable(root)), sum(not page.heading_associations and not page.heading_candidates for page in document.pages), int((time.monotonic() - started_at) * 1000))
        return result


def _repeated_furniture(document: ParsedDocument) -> frozenset[str]:
    occurrences: dict[str, set[int]] = {}
    for page in document.pages:
        for block in page.blocks:
            if block.source_span.table_id is not None or block.source_span.table_cell is not None or block.bbox is None:
                continue
            key = _furniture_key(block.source_text)
            if key and (block.font_size or 0) <= 9 and (block.bbox[1] < 60 or block.bbox[3] > 720):
                occurrences.setdefault(key, set()).add(page.page_number)
    return frozenset(key for key, pages in occurrences.items() if len(pages) > 1)


def _furniture_key(text: str) -> str:
    return re.sub(r"\d+", "#", normalized_key(text))


def _freeze(node: _MutableNode, document_id: str, path: tuple[int, ...] = ()) -> SectionNode:
    node_id = "root" if not path else "sec_" + hashlib.sha256((document_id + "/" + ".".join(map(str, path))).encode()).hexdigest()[:12]
    spans = (node.span,) if node.span else ()
    return SectionNode(node_id, node.title, node.level, ContentType.NARRATIVE, spans, tuple(node.block_ids), tuple(_freeze(child, document_id, path + (index,)) for index, child in enumerate(node.children)), tuple(node.source_segments), node.hierarchy_resolved)


def _heading_key(block_id: str, association: object) -> tuple[str, int, int]:
    start = int(_value(association, "source_char_start", 0) or 0)
    end = int(_value(association, "source_char_end", start) or start)
    return block_id, start, end


def _heading_range(block: object, association: object) -> tuple[int, int]:
    text = str(getattr(block, "source_text", ""))
    start = _value(association, "source_char_start")
    end = _value(association, "source_char_end")
    if type(start) is int and type(end) is int and 0 <= start <= end <= len(text):
        return start, end
    heading_text = str(_value(association, "heading_text", "") or "")
    found = text.find(heading_text) if heading_text else -1
    return (found, found + len(heading_text)) if found >= 0 else (0, len(text))


def _slice_span(span: SourceSpan, start: int, end: int) -> SourceSpan:
    base = span.char_start or 0
    return SourceSpan(span.page_number, span.block_index, base + start, base + end,
                      bbox=span.bbox, table_id=span.table_id, table_cell=span.table_cell)


def _append_range(node: _MutableNode, block: object, start: int, end: int) -> None:
    text = str(getattr(block, "source_text", ""))
    start = max(0, min(start, len(text)))
    end = max(start, min(end, len(text)))
    while start < end and text[start].isspace():
        start += 1
    while end > start and text[end - 1].isspace():
        end -= 1
    if start == end:
        return
    block_id = str(getattr(block, "block_id"))
    if block_id not in node.block_ids:
        node.block_ids.append(block_id)
    node.source_segments.append(SourceSegment(text[start:end], _slice_span(getattr(block, "source_span"), start, end)))


def _is_page_marker(block: object) -> bool:
    text = str(getattr(block, "source_text", "")).strip()
    bbox = getattr(block, "bbox", None)
    font_size = getattr(block, "font_size", None) or 0
    if not bbox or font_size > 9 or not (bbox[1] < 60 or bbox[3] > 720):
        return False
    return bool(re.fullmatch(r"(?:page\s*)?\d+(?:\s*(?:of|/)\s*\d+)?", text, re.IGNORECASE))


def _is_repeated_furniture_block(block: object, repeated: frozenset[str]) -> bool:
    bbox = getattr(block, "bbox", None)
    return bool(bbox and (getattr(block, "font_size", None) or 0) <= 9
                and (bbox[1] < 60 or bbox[3] > 720)
                and _furniture_key(str(getattr(block, "source_text", ""))) in repeated)


def _walk_mutable(node: _MutableNode):
    yield node, ()
    for child in node.children:
        yield from _walk_mutable(child)


def _value(item: object, name: str, default: object = None) -> object:
    return item.get(name, default) if isinstance(item, Mapping) else getattr(item, name, default)


def _diagnostics_enabled() -> bool:
    return os.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "").lower() in {"1", "true", "yes", "on"}
