"""Assemble immutable chunks from split pieces."""

from __future__ import annotations

from preprocessing_agent.domain import Chunk, ChunkCandidate
from preprocessing_agent.utils.ids import chunk_id
from preprocessing_agent.utils.tokens import count_tokens
from .splitter import ChunkSplitter


class ChunkAssembler:
    def __init__(self, splitter: ChunkSplitter) -> None:
        self.splitter = splitter

    def assemble(self, candidates: tuple[ChunkCandidate, ...] | list[ChunkCandidate]) -> tuple[Chunk, ...]:
        chunks = []
        for candidate in candidates:
            for piece in self.splitter.split(candidate):
                chunks.append(Chunk(
                    chunk_id=chunk_id(piece.source_text), canonical_key=piece.canonical_key,
                    content_type=candidate.content_type, source_text=piece.source_text,
                    embedding_text=piece.source_text, token_count=count_tokens(piece.source_text),
                    source_spans=piece.source_spans, section_path=candidate.section_path,
                    parent_key=piece.parent_key,
                ))
        return tuple(chunks)
