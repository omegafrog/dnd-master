"""Plan semantic candidates from an already-built DocumentTree."""

from __future__ import annotations

import re

from preprocessing_agent.domain import ChunkCandidate, ContentType, DocumentTree, ParsedDocument, SectionNode
from preprocessing_agent.utils.hashing import content_hash


def _key(value: str) -> str:
    value = re.sub(r"[^a-z0-9]+", "_", value.lower()).strip("_")
    return value or "section"


class ChunkPlanner:
    def plan(self, tree: DocumentTree, document: ParsedDocument) -> tuple[ChunkCandidate, ...]:
        blocks = {block.block_id: block for page in document.pages for block in page.blocks}
        output: list[ChunkCandidate] = []

        def visit(node: SectionNode, path: tuple[str, ...]) -> None:
            current_path = path + ((_key(node.title),) if node.level else ())
            source_blocks = [blocks[item] for item in node.block_ids if item in blocks]
            text = "\n\n".join(block.source_text for block in source_blocks).strip()
            spans = tuple(block.source_span for block in source_blocks)
            if text:
                canonical = ".".join(current_path) or _key(document.document_id)
                candidate_id = f"cand_{content_hash(canonical + "\n" + text)[:16]}"
                output.append(ChunkCandidate(candidate_id, canonical, node.content_type, text, spans, current_path))
            for child in node.children:
                visit(child, current_path)

        visit(tree.root, ())
        return tuple(output)
