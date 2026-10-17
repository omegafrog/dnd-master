"""Chunk policy value objects and profile loading."""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Mapping

import yaml

from preprocessing_agent.domain import ContentType


@dataclass(frozen=True, slots=True)
class ChunkPolicy:
    target_tokens: int = 350
    max_tokens: int = 500
    prose_max_tokens: int = 300
    max_characters: int = 6000
    min_tokens: int = 100
    overlap_tokens: int = 30
    strategies: Mapping[ContentType, str] = field(default_factory=lambda: {
        ContentType.SPELL: "atomic", ContentType.MONSTER_STAT_BLOCK: "atomic",
        ContentType.CLASS_FEATURE: "atomic", ContentType.RACE_TRAIT: "atomic",
        ContentType.CONDITION: "atomic", ContentType.MAGIC_ITEM: "atomic",
        ContentType.TABLE: "table", ContentType.RULE: "rule",
        ContentType.NARRATIVE: "narrative",
    })

    def __post_init__(self) -> None:
        if not (0 < self.min_tokens <= self.target_tokens <= self.max_tokens):
            raise ValueError("chunk token limits must satisfy min <= target <= max")
        if self.prose_max_tokens <= 0:
            raise ValueError("prose_max_tokens must be positive")
        if self.max_characters <= 0:
            raise ValueError("maximum chunk characters must be positive")
        if not 0 <= self.overlap_tokens < min(self.max_tokens, self.prose_max_tokens):
            raise ValueError("overlap_tokens must be non-negative and below the active chunk limit")

    def strategy_for(self, content_type: ContentType) -> str:
        return self.strategies.get(content_type, "narrative")


def load_policy(path: str | Path) -> ChunkPolicy:
    raw = yaml.safe_load(Path(path).read_text(encoding="utf-8")) or {}
    values = raw.get("chunking", {})
    strategies = values.get("strategies", {})
    mapped = {ContentType(key): value for key, value in strategies.items()}
    return ChunkPolicy(
        target_tokens=values.get("target_tokens", 350),
        max_tokens=values.get("max_tokens", 500),
        prose_max_tokens=values.get("prose_max_tokens", 300),
        max_characters=values.get("max_characters", 6000),
        min_tokens=values.get("min_tokens", 100),
        overlap_tokens=values.get("overlap_tokens", 30),
        strategies=mapped or ChunkPolicy().strategies,
    )
