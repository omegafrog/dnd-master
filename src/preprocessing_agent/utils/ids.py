"""Stable identifiers for chunk artifacts."""

from __future__ import annotations

from .hashing import content_hash


def chunk_id(source_text: str) -> str:
    return f"chk_{content_hash(source_text)}"
