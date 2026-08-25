"""Build an immutable ordered document tree from parsed blocks."""

from __future__ import annotations

from dataclasses import dataclass, field
import hashlib

from preprocessing_agent.domain import ContentType, DocumentTree, ParsedDocument, SectionNode, SourceSpan
from .detector import HeadingDetector


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
        root = _MutableNode(document.document_id, 0, None)
        stack: list[_MutableNode] = [root]
        for page in document.pages:
            for block in page.blocks:
                decision = self.detector.detect(block)
                if decision.is_heading:
                    level = max(1, decision.level or 1)
                    while len(stack) > 1 and stack[-1].level >= level:
                        stack.pop()
                    node = _MutableNode(block.source_text.strip(), level, block.source_span, [block.block_id])
                    stack[-1].children.append(node)
                    stack.append(node)
                else:
                    stack[-1].block_ids.append(block.block_id)
        return DocumentTree(document.document_id, _freeze(root, document.document_id))


def _freeze(node: _MutableNode, document_id: str, path: tuple[int, ...] = ()) -> SectionNode:
    node_id = "root" if not path else "sec_" + hashlib.sha256((document_id + "/" + ".".join(map(str, path))).encode()).hexdigest()[:12]
    spans = (node.span,) if node.span else ()
    return SectionNode(node_id, node.title, node.level, ContentType.NARRATIVE, spans, tuple(node.block_ids), tuple(_freeze(child, document_id, path + (index,)) for index, child in enumerate(node.children)))
