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


def _anchored_key(base: str, node: SectionNode, spans: tuple) -> str:
    anchor = spans[0] if spans else None
    page = anchor.page_number if anchor else 0
    block = anchor.block_index if anchor and anchor.block_index is not None else 0
    suffix = f"{node.node_id}.p{page:04d}.b{block:04d}"
    if len(base) + len(suffix) + 1 <= _CANONICAL_KEY_MAX_LENGTH:
        return f"{base}.{suffix}"
    stable_hash = base.rsplit("_", 1)[-1] if re.fullmatch(r"[0-9a-f]{12}", base.rsplit("_", 1)[-1]) else content_hash(base)[:12]
    prefix = base[:-13] if base.endswith("_" + stable_hash) else base
    available = _CANONICAL_KEY_MAX_LENGTH - len(suffix) - len(stable_hash) - 2
    return f"{prefix[:available].rstrip('_')}.{suffix}_{stable_hash}"


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
                canonical = _anchored_key(_bounded_key(".".join(current_path) or _key(document.document_id)), node, spans)
                candidate_id = f"cand_{content_hash(canonical + "\n" + text)[:16]}"
                output.append(ChunkCandidate(
                    candidate_id, canonical, node.content_type, text, spans, current_path,
                    source_segments=tuple(SourceSegment(block.source_text, block.source_span) for block in source_blocks),
                ))
            for child in node.children:
                visit(child, current_path)

        visit(tree.root, ())
        return tuple(output)
