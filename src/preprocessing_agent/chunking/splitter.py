"""Deterministic boundary-aware splitting; source text is never rewritten semantically."""

from __future__ import annotations

import re
import logging
import os
from dataclasses import dataclass

from preprocessing_agent.domain import ChunkCandidate, ContentType, SourceSegment, SourceSpan
from preprocessing_agent.utils.tokens import count_tokens, tokenize
from .policy import ChunkPolicy

_LOGGER = logging.getLogger(__name__)


@dataclass(frozen=True, slots=True)
class SplitPiece:
    source_text: str
    source_spans: tuple[SourceSpan, ...]
    canonical_key: str
    parent_key: str | None = None
    provenance_error: str | None = None


@dataclass(frozen=True, slots=True)
class _TextRange:
    start: int
    end: int


class ChunkSplitter:
    def __init__(self, policy: ChunkPolicy) -> None:
        self.policy = policy

    def split(self, candidate: ChunkCandidate) -> tuple[SplitPiece, ...]:
        tokens = tokenize(candidate.source_text)
        strategy = self.policy.strategy_for(candidate.content_type)
        if strategy == "table" or (len(tokens) <= self.policy.max_tokens and len(candidate.source_text) <= self.policy.max_characters):
            return (SplitPiece(candidate.source_text, candidate.source_spans, candidate.canonical_key),)
        if strategy == "atomic":
            return self._atomic(candidate, tokens)
        parts = self._semantic_parts(candidate.source_text)
        return self._window(candidate, parts)

    def _atomic(self, candidate: ChunkCandidate, tokens: list[str]) -> tuple[SplitPiece, ...]:
        pieces = []
        token_ranges = [match.span() for match in re.finditer(r"\S+", candidate.source_text)]
        step = self.policy.max_tokens - self.policy.overlap_tokens
        for start in range(0, len(tokens), step):
            end = min(start + self.policy.max_tokens, len(tokens))
            pieces.append(self._piece(
                candidate, token_ranges[start][0], token_ranges[end - 1][1],
                f"{candidate.canonical_key}.part-{len(pieces) + 1:03d}", candidate.canonical_key,
            ))
            if end == len(tokens):
                break
        return tuple(pieces)

    def _semantic_parts(self, text: str) -> list[_TextRange]:
        sections = [self._trimmed_range(text, match.span()) for match in re.finditer(r"[^\n]*(?:\n(?!\s*\n)[^\n]*)*", text)]
        sections = [section for section in sections if section is not None]
        if any(count_tokens(text[section.start:section.end]) > self.policy.max_tokens or
               section.end - section.start > self.policy.max_characters for section in sections):
            expanded: list[_TextRange] = []
            for section in sections:
                part = text[section.start:section.end]
                if count_tokens(part) <= self.policy.max_tokens and len(part) <= self.policy.max_characters:
                    expanded.append(section)
                    continue
                for sentence in re.finditer(r".*?(?:[.!?](?:\s+|$)|$)", part, re.DOTALL):
                    sentence_range = self._trimmed_range(text, (section.start + sentence.start(), section.start + sentence.end()))
                    if sentence_range is not None:
                        expanded.append(sentence_range)
            sections = expanded
        return sections

    def _window(self, candidate: ChunkCandidate, parts: list[_TextRange]) -> tuple[SplitPiece, ...]:
        result: list[SplitPiece] = []
        current: list[_TextRange] = []
        current_count = 0
        current_chars = 0
        for part in parts:
            part_text = candidate.source_text[part.start:part.end]
            part_count = count_tokens(part_text)
            if current and (current_count + part_count > self.policy.max_tokens or
                            current_chars + len(part_text) + 2 > self.policy.max_characters):
                result.append(self._piece(
                    candidate, current[0].start, current[-1].end,
                    f"{candidate.canonical_key}.part-{len(result) + 1:03d}",
                ))
                overlap_start = self._overlap_start(candidate.source_text, current[-1], self.policy.overlap_tokens)
                current = [_TextRange(overlap_start, current[-1].end)] if overlap_start is not None else []
                current_count = count_tokens(candidate.source_text[overlap_start:current[-1].end]) if current else 0
                current_chars = len(candidate.source_text[current[0].start:current[-1].end]) if current else 0
                if current and (current_count + part_count > self.policy.max_tokens or
                                current_chars + len(part_text) + 2 > self.policy.max_characters):
                    current, current_count, current_chars = [], 0, 0
            if part_count > self.policy.max_tokens or len(part_text) > self.policy.max_characters:
                for start, end in self._bounded_ranges(part_text):
                    result.append(self._piece(
                        candidate, part.start + start, part.start + end,
                        f"{candidate.canonical_key}.part-{len(result) + 1:03d}",
                    ))
                current, current_count, current_chars = [], 0, 0
            else:
                current.append(part)
                current_count += part_count
                current_chars += len(part_text) + (2 if len(current) > 1 else 0)
        if current:
            result.append(self._piece(
                candidate, current[0].start, current[-1].end,
                f"{candidate.canonical_key}.part-{len(result) + 1:03d}" if result else candidate.canonical_key,
            ))
        if len(candidate.source_text) > self.policy.max_characters and _diagnostics_enabled():
            _LOGGER.info("dev_chunk_splitter stage=complete canonicalKey=%s expectedMaxCharacters=%d actualSourceCharacters=%d actualPieceCount=%d actualMaxPieceCharacters=%d outcome=split",
                         candidate.canonical_key, self.policy.max_characters, len(candidate.source_text), len(result),
                         max((len(piece.source_text) for piece in result), default=0))
        return tuple(result)

    def _bounded_ranges(self, text: str) -> list[tuple[int, int]]:
        ranges: list[tuple[int, int]] = []
        tokens = list(re.finditer(r"\S+", text))
        start = 0
        last_end = 0
        count = 0
        for token in tokens:
            if count and (count + 1 > self.policy.max_tokens or
                          token.end() - start > self.policy.max_characters):
                ranges.append((start, last_end))
                start = token.start()
                count = 0
            if token.end() - start > self.policy.max_characters:
                if start < token.start():
                    ranges.append((start, token.start()))
                for offset in range(token.start(), token.end(), self.policy.max_characters):
                    ranges.append((offset, min(offset + self.policy.max_characters, token.end())))
                start = token.end()
                last_end = start
                count = 0
            else:
                count += 1
                last_end = token.end()
        if start < len(text):
            ranges.append((start, len(text)))
        return ranges

    @staticmethod
    def _trimmed_range(text: str, bounds: tuple[int, int]) -> _TextRange | None:
        start, end = bounds
        while start < end and text[start].isspace():
            start += 1
        while end > start and text[end - 1].isspace():
            end -= 1
        return _TextRange(start, end) if start < end else None

    @staticmethod
    def _overlap_start(text: str, part: _TextRange, overlap_tokens: int) -> int | None:
        matches = list(re.finditer(r"\S+", text[part.start:part.end]))
        if not matches or overlap_tokens == 0:
            return None
        return part.start + matches[max(0, len(matches) - overlap_tokens)].start()

    def _piece(self, candidate: ChunkCandidate, start: int, end: int, canonical_key: str,
               parent_key: str | None = None) -> SplitPiece:
        source_spans, error = self._trace(candidate, start, end)
        return SplitPiece(candidate.source_text[start:end], source_spans, canonical_key, parent_key, error)


    def _trace(self, candidate: ChunkCandidate, start: int, end: int) -> tuple[tuple[SourceSpan, ...], str | None]:
        segments = candidate.source_segments
        if not segments and len(candidate.source_spans) == 1:
            segments = (SourceSegment(candidate.source_text, candidate.source_spans[0]),)
        if not segments:
            return (), "source segment mapping unavailable"

        ranges: list[tuple[SourceSegment, int, int]] = []
        cursor = 0
        for segment in segments:
            if not candidate.source_text.startswith(segment.source_text, cursor):
                return (), "source segment mapping unavailable"
            segment_start = cursor
            cursor += len(segment.source_text)
            ranges.append((segment, segment_start, cursor))
            if cursor < len(candidate.source_text) and candidate.source_text.startswith("\n\n", cursor):
                cursor += 2
        if cursor != len(candidate.source_text):
            return (), "source segment mapping unavailable"

        spans = tuple(self._clip_span(segment.source_span, max(start, left), min(end, right), left)
                      for segment, left, right in ranges if start < right and end > left)
        return (spans, None) if spans else ((), "source segment mapping unavailable")

    @staticmethod
    def _clip_span(span: SourceSpan, start: int, end: int, segment_start: int) -> SourceSpan:
        if span.char_start is None or span.char_end is None:
            return span
        return SourceSpan(
            page_number=span.page_number, block_index=span.block_index,
            char_start=span.char_start + start - segment_start,
            char_end=span.char_start + end - segment_start,
            token_start=span.token_start, token_end=span.token_end, bbox=span.bbox,
            table_id=span.table_id, table_cell=span.table_cell,
        )


def _diagnostics_enabled() -> bool:
    return os.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "").lower() in {"1", "true", "yes", "on"}
