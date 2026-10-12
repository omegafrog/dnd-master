"""Build an immutable ordered document tree from parsed blocks."""

from __future__ import annotations

from dataclasses import dataclass, field
import hashlib
import logging
import os
import time
from collections.abc import Mapping

from preprocessing_agent.domain import ContentType, DocumentTree, ParsedDocument, SectionNode, SourceSpan
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
        for page in document.pages:
            associations = {_value(item, "heading_block_id"): item for item in page.heading_associations}
            associated_with = {
                block_id: heading_id
                for heading_id, association in associations.items()
                for block_id in _value(association, "associated_block_ids", ())
            }
            nodes_by_heading: dict[str, _MutableNode] = {}
            for block in page.blocks:
                edge_furniture = block.bbox is not None and (block.font_size or 0) <= 9 and (block.bbox[1] < 60 or block.bbox[3] > 720)
                if normalized_key(block.source_text) in repeated or edge_furniture:
                    continue
                heading = associations.get(block.block_id)
                decision = self.detector.detect(block) if not page.heading_associations else None
                if heading is not None or decision is not None and decision.is_heading:
                    level = max(1, int(_value(heading, "level", 1) if heading is not None else decision.level or 1))
                    while len(stack) > 1 and stack[-1].level >= level:
                        stack.pop()
                    node = _MutableNode(block.source_text.strip(), level, block.source_span, [block.block_id])
                    stack[-1].children.append(node)
                    stack.append(node)
                    nodes_by_heading[block.block_id] = node
                elif block.block_id in associated_with and associated_with[block.block_id] in nodes_by_heading:
                    nodes_by_heading[associated_with[block.block_id]].block_ids.append(block.block_id)
                else:
                    stack[-1].block_ids.append(block.block_id)
        result = DocumentTree(document.document_id, _freeze(root, document.document_id))
        if _diagnostics_enabled():
            _LOGGER.info("dev_document_tree stage=complete documentId=%s pageCount=%d associationCount=%d detectorFallbackPages=%d elapsedMs=%d outcome=success", document.document_id, len(document.pages), sum(len(page.heading_associations) for page in document.pages), sum(not bool(page.heading_associations) for page in document.pages), int((time.monotonic() - started_at) * 1000))
        return result


def _repeated_furniture(document: ParsedDocument) -> frozenset[str]:
    occurrences: dict[str, set[int]] = {}
    for page in document.pages:
        for block in page.blocks:
            if block.bbox is None:
                continue
            key = normalized_key(block.source_text)
            if key and (block.font_size or 0) <= 9 and (block.bbox[1] < 60 or block.bbox[1] > 720):
                occurrences.setdefault(key, set()).add(page.page_number)
    return frozenset(key for key, pages in occurrences.items() if len(pages) > 1)


def _freeze(node: _MutableNode, document_id: str, path: tuple[int, ...] = ()) -> SectionNode:
    node_id = "root" if not path else "sec_" + hashlib.sha256((document_id + "/" + ".".join(map(str, path))).encode()).hexdigest()[:12]
    spans = (node.span,) if node.span else ()
    return SectionNode(node_id, node.title, node.level, ContentType.NARRATIVE, spans, tuple(node.block_ids), tuple(_freeze(child, document_id, path + (index,)) for index, child in enumerate(node.children)))


def _value(item: object, name: str, default: object = None) -> object:
    return item.get(name, default) if isinstance(item, Mapping) else getattr(item, name, default)


def _diagnostics_enabled() -> bool:
    return os.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "").lower() in {"1", "true", "yes", "on"}
