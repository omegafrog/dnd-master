"""Plan semantic candidates from an already-built DocumentTree."""

from __future__ import annotations

import re

from preprocessing_agent.domain import ChunkCandidate, ContentType, DocumentTree, ParsedDocument, SectionNode, SourceSegment
from preprocessing_agent.utils.hashing import content_hash

_CANONICAL_KEY_MAX_LENGTH = 160


def _key(value: str) -> str:
    value = re.sub(r"^(\d{1,4})(?:\1|[_ -]+\1)(?:[_ -]+)", "", value.lower())
    value = re.sub(r"[^a-z0-9]+", "_", value).strip("_")
    return value or "section"


def _bounded_key(value: str) -> str:
    if len(value) <= _CANONICAL_KEY_MAX_LENGTH:
        return value
    suffix = content_hash(value)[:12]
    return f"{value[:_CANONICAL_KEY_MAX_LENGTH - len(suffix) - 1].rstrip('_')}_{suffix}"


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
                canonical = _bounded_key(".".join(current_path) or _key(document.document_id))
                candidate_id = f"cand_{content_hash(canonical + "\n" + text)[:16]}"
                output.append(ChunkCandidate(
                    candidate_id, canonical, node.content_type, text, spans, current_path,
                    source_segments=tuple(SourceSegment(block.source_text, block.source_span) for block in source_blocks),
                ))
            for child in node.children:
                visit(child, current_path)

        visit(tree.root, ())
        return tuple(output)
