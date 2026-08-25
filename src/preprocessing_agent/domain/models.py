"""Immutable contracts shared by the preprocessing pipeline."""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from types import MappingProxyType
from typing import Any, Mapping


class ContentType(str, Enum):
    NARRATIVE = "narrative"
    RULE = "rule"
    TABLE = "table"
    CLASS_FEATURE = "class_feature"
    RACE_TRAIT = "race_trait"
    SPELL = "spell"
    MONSTER_STAT_BLOCK = "monster_stat_block"
    MAGIC_ITEM = "magic_item"
    CONDITION = "condition"
    EQUIPMENT = "equipment"
    BACKGROUND = "background"
    UNKNOWN = "unknown"


def _freeze(value: Any) -> Any:
    if isinstance(value, Mapping):
        return MappingProxyType({key: _freeze(item) for key, item in value.items()})
    if isinstance(value, list):
        return tuple(_freeze(item) for item in value)
    if isinstance(value, set):
        return frozenset(_freeze(item) for item in value)
    return value


@dataclass(frozen=True, slots=True)
class SourceSpan:
    page_number: int
    block_index: int | None = None
    char_start: int | None = None
    char_end: int | None = None
    token_start: int | None = None
    token_end: int | None = None

    def __post_init__(self) -> None:
        if self.page_number < 1:
            raise ValueError("page_number must be positive")
        if self.block_index is not None and self.block_index < 0:
            raise ValueError("block_index must be non-negative")
        for start, end, label in (
            (self.char_start, self.char_end, "character"),
            (self.token_start, self.token_end, "token"),
        ):
            if start is not None and start < 0:
                raise ValueError(f"{label} start must be non-negative")
            if end is not None and end < 0:
                raise ValueError(f"{label} end must be non-negative")
            if start is not None and end is not None and start > end:
                raise ValueError(f"{label} start must not exceed end")


@dataclass(frozen=True, slots=True)
class SourceSegment:
    """Exact candidate text associated with one page-local source span."""

    source_text: str
    source_span: SourceSpan

    def __post_init__(self) -> None:
        if not self.source_text:
            raise ValueError("source_text is required")


@dataclass(frozen=True, slots=True)
class ParsedBlock:
    block_id: str
    source_text: str
    source_span: SourceSpan
    bbox: tuple[float, float, float, float] | None = None
    font_size: float | None = None
    font_weight: str | None = None

    def __post_init__(self) -> None:
        if not self.block_id:
            raise ValueError("block_id is required")
        if not self.source_text:
            raise ValueError("source_text is required")
        if self.bbox is not None and len(self.bbox) != 4:
            raise ValueError("bbox must contain four coordinates")


@dataclass(frozen=True, slots=True)
class ParsedPage:
    page_number: int
    blocks: tuple[ParsedBlock, ...]
    source_text: str

    def __post_init__(self) -> None:
        if self.page_number < 1:
            raise ValueError("page_number must be positive")


@dataclass(frozen=True, slots=True)
class ParsedDocument:
    document_id: str
    source_path: str
    source_text: str
    pages: tuple[ParsedPage, ...]
    metadata: Mapping[str, Any] = field(default_factory=dict)

    def __post_init__(self) -> None:
        if not self.document_id or not self.source_path:
            raise ValueError("document_id and source_path are required")
        object.__setattr__(self, "metadata", _freeze(self.metadata))


@dataclass(frozen=True, slots=True)
class SectionNode:
    node_id: str
    title: str
    level: int
    content_type: ContentType
    source_spans: tuple[SourceSpan, ...] = ()
    block_ids: tuple[str, ...] = ()
    children: tuple["SectionNode", ...] = ()

    def __post_init__(self) -> None:
        if not self.node_id or not self.title:
            raise ValueError("node_id and title are required")
        if self.level < 0:
            raise ValueError("level must be non-negative")


@dataclass(frozen=True, slots=True)
class DocumentTree:
    document_id: str
    root: SectionNode


@dataclass(frozen=True, slots=True)
class ChunkCandidate:
    candidate_id: str
    canonical_key: str
    content_type: ContentType
    source_text: str
    source_spans: tuple[SourceSpan, ...]
    section_path: tuple[str, ...] = ()
    parent_key: str | None = None
    source_segments: tuple[SourceSegment, ...] = ()


@dataclass(frozen=True, slots=True)
class Chunk:
    chunk_id: str
    canonical_key: str
    content_type: ContentType
    source_text: str
    embedding_text: str
    token_count: int
    source_spans: tuple[SourceSpan, ...]
    section_path: tuple[str, ...] = ()
    parent_key: str | None = None

    def __post_init__(self) -> None:
        if not self.chunk_id or not self.canonical_key:
            raise ValueError("chunk_id and canonical_key are required")
        if self.token_count < 0:
            raise ValueError("token_count must be non-negative")


@dataclass(frozen=True, slots=True)
class ValidationIssue:
    issue_type: str
    message: str
    severity: str = "error"
    path: str | None = None
    source_span: SourceSpan | None = None

    def __post_init__(self) -> None:
        if self.severity not in {"error", "warning", "info"}:
            raise ValueError("severity must be error, warning, or info")


@dataclass(frozen=True, slots=True)
class ValidationResult:
    valid: bool
    issues: tuple[ValidationIssue, ...] = ()
    checked_chunk_ids: tuple[str, ...] = ()
