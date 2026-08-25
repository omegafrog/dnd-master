from __future__ import annotations
from dataclasses import dataclass
from typing import Iterable
from preprocessing_agent.domain import Chunk
@dataclass(frozen=True, slots=True)
class GoldMapping:
    keys: tuple[str, ...]
    unmatched: tuple[str, ...]
class GoldContextMapper:
    def map(self, gold_keys: Iterable[str], chunks: Iterable[Chunk]) -> GoldMapping:
        by_key = {chunk.canonical_key: chunk.chunk_id for chunk in chunks}
        keys, unmatched = [], []
        for key in gold_keys:
            chunk_id = by_key.get(key)
            (keys if chunk_id is not None else unmatched).append(chunk_id or key)
        return GoldMapping(tuple(keys), tuple(unmatched))
