"""Plan semantic candidates from an already-built DocumentTree."""

from __future__ import annotations

import re
import logging
import os
import time
from collections.abc import Mapping

from preprocessing_agent.domain import ChunkCandidate, ContentType, DocumentTree, ParsedDocument, SectionNode, SourceSegment
from preprocessing_agent.utils.hashing import content_hash

_CANONICAL_KEY_MAX_LENGTH = 160
_LOGGER = logging.getLogger(__name__)


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
        started_at = time.monotonic()
        blocks = {block.block_id: block for page in document.pages for block in page.blocks}
        tables = tuple((page.page_number, table) for page in document.pages for table in page.tables)
        table_block_ids = {block_id for _, table in tables for cell in _value(table, "cells", ()) for block_id in _value(cell, "source_block_ids", ())}
        if _diagnostics_enabled():
            _LOGGER.info("dev_chunk_planner stage=start documentId=%s blockCount=%d tableCount=%d outcome=started", document.document_id, len(blocks), len(tables))
        output: list[ChunkCandidate] = []
        emitted_table_ids: set[tuple[int, str]] = set()

        def visit(node: SectionNode, path: tuple[str, ...]) -> None:
            current_path = path + ((_key(node.title),) if node.level else ())
            source_blocks = [blocks[item] for item in node.block_ids if item in blocks and item not in table_block_ids]
            text = "\n\n".join(block.source_text for block in source_blocks).strip()
            spans = tuple(block.source_span for block in source_blocks)
            if text:
                canonical = _anchored_key(_bounded_key(".".join(current_path) or _key(document.document_id)), node, spans)
                candidate_id = "cand_" + content_hash(canonical + "\n" + text)[:16]
                output.append(ChunkCandidate(
                    candidate_id, canonical, node.content_type, text, spans, current_path,
                    source_segments=tuple(SourceSegment(block.source_text, block.source_span) for block in source_blocks),
                ))
            node_ids = set(node.block_ids)
            for page_number, table in tables:
                table_id = str(_value(table, "table_id", "table"))
                table_key = (page_number, table_id)
                if table_key in emitted_table_ids:
                    continue
                cells = tuple(_value(table, "cells", ()))
                if not node_ids.intersection(block_id for cell in cells for block_id in _value(cell, "source_block_ids", ())):
                    continue
                table_text, table_spans, serialized_cell_count, table_segments = _table_text(table, blocks)
                if _diagnostics_enabled():
                    _LOGGER.info("dev_chunk_planner stage=table_source_mapping documentId=%s pageNumber=%d tableId=%s expectedMappedCellCount=%d actualMappedCellCount=%d actualSourceSpanCount=%d outcome=%s", document.document_id, page_number, table_id, serialized_cell_count, len(table_segments), len(table_spans), "success" if len(table_segments) == serialized_cell_count and bool(table_spans) else "incomplete")
                if not table_text or not table_spans or len(table_segments) != serialized_cell_count:
                    if _diagnostics_enabled():
                        reason = ("empty_serialized_cells" if not table_text else "no_source_spans" if not table_spans
                                  else "unmapped_cell_source_text")
                        _LOGGER.info("dev_chunk_planner stage=skip_table documentId=%s pageNumber=%d tableId=%s expectedCellCount=%d actualSerializedCellCount=%d expectedMappedCellCount=%d actualMappedCellCount=%d actualSourceSpanCount=%d skipReason=%s recoveryAction=skip_table outcome=skipped", document.document_id, page_number, table_id, len(cells), serialized_cell_count, serialized_cell_count, len(table_segments), len(table_spans), reason)
                    continue
                emitted_table_ids.add(table_key)
                anchor = table_spans[0]
                base = _bounded_key((".".join(current_path) or _key(document.document_id)) + ".table_" + _key(table_id))
                canonical = _anchored_key(base, node, (anchor,))
                output.append(ChunkCandidate(
                    "cand_" + content_hash(canonical + "\n" + table_text)[:16], canonical,
                    ContentType.TABLE, table_text, table_spans, current_path,
                    source_segments=table_segments,
                ))
            for child in node.children:
                visit(child, current_path)

        visit(tree.root, ())
        if _diagnostics_enabled():
            _LOGGER.info("dev_chunk_planner stage=complete documentId=%s blockCount=%d tableCount=%d candidateCount=%d elapsedMs=%d outcome=success", document.document_id, len(blocks), len(tables), len(output), int((time.monotonic() - started_at) * 1000))
        return tuple(output)


def _value(item: object, name: str, default: object = None) -> object:
    return item.get(name, default) if isinstance(item, Mapping) else getattr(item, name, default)


def _diagnostics_enabled() -> bool:
    return os.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "").lower() in {"1", "true", "yes", "on"}


def _table_text(table: object, blocks: dict) -> tuple[str, tuple, int, tuple[SourceSegment, ...]]:
    rows = (*tuple(_value(table, "header_rows", ())), *tuple(_value(table, "rows", ())))
    parts: list[str] = []
    spans = []
    segments = []
    serialized_cell_count = 0
    for row in rows:
        cells = tuple(_value(row, "cells", ()))
        values = []
        for cell in cells:
            value = str(_value(cell, "text", ""))
            if value:
                serialized_cell_count += 1
                values.append(value)
                source_blocks = [blocks[item] for item in _value(cell, "source_block_ids", ()) if item in blocks]
                matching_block = next((block for block in source_blocks if block.source_text == value), None)
                if matching_block is not None:
                    segments.append(SourceSegment(value, matching_block.source_span))
            spans.extend(blocks[item].source_span for item in _value(cell, "source_block_ids", ()) if item in blocks)
        if values:
            parts.append(" | ".join(values))
    return "\n".join(parts), tuple(dict.fromkeys(spans)), serialized_cell_count, tuple(segments)
