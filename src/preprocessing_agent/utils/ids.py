"""Stable identifiers for chunk artifacts."""

from __future__ import annotations

from .hashing import content_hash


def chunk_id(source_text: str, canonical_key: str) -> str:
    identity = canonical_key + "\0" + source_text
    return f"chk_{content_hash(identity)}"
