"""Small, deterministic token utilities used by chunking."""

from __future__ import annotations

import re

_TOKEN = re.compile(r"\S+")


def tokenize(text: str) -> list[str]:
    return _TOKEN.findall(text)


def count_tokens(text: str) -> int:
    return len(tokenize(text))


def join_tokens(tokens: list[str] | tuple[str, ...]) -> str:
    return " ".join(tokens)
