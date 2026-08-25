"""Deterministic boundary-aware splitting; source text is never rewritten semantically."""

from __future__ import annotations

import re
from dataclasses import dataclass

from preprocessing_agent.domain import ChunkCandidate, ContentType, SourceSpan
from preprocessing_agent.utils.tokens import count_tokens, join_tokens, tokenize
from .policy import ChunkPolicy


@dataclass(frozen=True, slots=True)
class SplitPiece:
    source_text: str
    source_spans: tuple[SourceSpan, ...]
    canonical_key: str
    parent_key: str | None = None


class ChunkSplitter:
    def __init__(self, policy: ChunkPolicy) -> None:
        self.policy = policy

    def split(self, candidate: ChunkCandidate) -> tuple[SplitPiece, ...]:
        tokens = tokenize(candidate.source_text)
        strategy = self.policy.strategy_for(candidate.content_type)
        if strategy == "table" or len(tokens) <= self.policy.max_tokens:
            return (SplitPiece(candidate.source_text, candidate.source_spans, candidate.canonical_key),)
        if strategy == "atomic":
            return self._atomic(candidate, tokens)
        parts = self._semantic_parts(candidate.source_text)
        return self._window(candidate, parts)

    def _atomic(self, candidate: ChunkCandidate, tokens: list[str]) -> tuple[SplitPiece, ...]:
        pieces = []
        for start in range(0, len(tokens), self.policy.max_tokens - self.policy.overlap_tokens):
            end = min(start + self.policy.max_tokens, len(tokens))
            pieces.append(SplitPiece(join_tokens(tokens[start:end]), candidate.source_spans,
                                     f"{candidate.canonical_key}.part-{len(pieces) + 1:03d}", candidate.canonical_key))
            if end == len(tokens):
                break
        return tuple(pieces)

    def _semantic_parts(self, text: str) -> list[str]:
        sections = [part.strip() for part in re.split(r"\n\s*\n", text) if part.strip()]
        if any(count_tokens(part) > self.policy.max_tokens for part in sections):
            expanded = []
            for part in sections:
                expanded.extend(sentence.strip() for sentence in re.split(r"(?<=[.!?])\s+", part) if sentence.strip())
            sections = expanded
        return sections

    def _window(self, candidate: ChunkCandidate, parts: list[str]) -> tuple[SplitPiece, ...]:
        result: list[SplitPiece] = []
        current: list[str] = []
        current_count = 0
        for part in parts:
            part_count = count_tokens(part)
            if current and current_count + part_count > self.policy.max_tokens:
                result.append(SplitPiece("\n\n".join(current), candidate.source_spans,
                                         f"{candidate.canonical_key}.part-{len(result) + 1:03d}"))
                overlap = tokenize(current[-1])[-self.policy.overlap_tokens:]
                current = [join_tokens(overlap)] if overlap else []
                current_count = len(overlap)
            if part_count > self.policy.max_tokens:
                words = tokenize(part)
                for index in range(0, len(words), self.policy.max_tokens):
                    result.append(SplitPiece(join_tokens(words[index:index + self.policy.max_tokens]), candidate.source_spans,
                                             f"{candidate.canonical_key}.part-{len(result) + 1:03d}"))
                current, current_count = [], 0
            else:
                current.append(part)
                current_count += part_count
        if current:
            result.append(SplitPiece("\n\n".join(current), candidate.source_spans,
                                     f"{candidate.canonical_key}.part-{len(result) + 1:03d}" if result else candidate.canonical_key))
        return tuple(result)
