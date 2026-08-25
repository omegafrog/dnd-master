"""Conservative, derived-text cleanup after chunk assembly.

Only ``embedding_text`` is normalized.  Source text and spans remain the
authoritative provenance contract.
"""

from __future__ import annotations

import re
from collections import Counter
from dataclasses import replace
from typing import Iterable

from preprocessing_agent.domain import Chunk
from preprocessing_agent.utils.tokens import count_tokens


_LINE_BREAK_HYPHEN = re.compile(r"(?<=[A-Za-z])[-\u00ad]\s*\n\s*(?=[a-z])")
_EXTRACTED_HYPHEN = re.compile(r"([a-z]{3,})-([a-z]{3,})")
_MARKDOWN_HEADING = re.compile(r"(?m)^(#{1,6}\s*[^\n]+)\n(?!\n)")


def _normalize_embedding_text(text: str) -> str:
    # The newline form is unambiguously a line-wrap artifact.  The second
    # form handles PDF extraction that discarded the line break; requiring
    # lower-case word fragments avoids touching ordinary punctuation.
    text = _LINE_BREAK_HYPHEN.sub("", text)
    text = _EXTRACTED_HYPHEN.sub(r"\1\2", text)
    text = _MARKDOWN_HEADING.sub(r"\1\n\n", text)
    return text


def _boundary_signature(line: str) -> str:
    return re.sub(r"\b\d+\b", "<page>", " ".join(line.split()).casefold())


def _repeated_boundary_lines(chunks: tuple[Chunk, ...]) -> frozenset[str]:
    counts: Counter[str] = Counter()
    for chunk in chunks:
        lines = [line.strip() for line in chunk.source_text.splitlines() if line.strip()]
        for line in {lines[0], lines[-1]} if lines else ():
            counts[_boundary_signature(line)] += 1
    return frozenset(signature for signature, count in counts.items()
                     if count >= 2 and len(signature.split()) <= 12)


def _remove_repeated_noise(text: str, repeated: frozenset[str]) -> str:
    if not repeated:
        return text
    lines = text.splitlines()
    while lines and _boundary_signature(lines[0].strip()) in repeated:
        lines.pop(0)
    while lines and _boundary_signature(lines[-1].strip()) in repeated:
        lines.pop()
    return "\n".join(lines)


def postprocess_chunks(chunks: Iterable[Chunk]) -> tuple[Chunk, ...]:
    """Return chunks with derived embedding text cleaned, never discarded."""
    items = tuple(chunks)
    repeated = _repeated_boundary_lines(items)
    result = []
    for chunk in items:
        embedding_text = _normalize_embedding_text(
            _remove_repeated_noise(chunk.source_text, repeated)
        )
        result.append(replace(chunk, embedding_text=embedding_text, token_count=count_tokens(embedding_text)))
    return tuple(result)
