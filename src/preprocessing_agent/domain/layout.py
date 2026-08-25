"""Vendor-neutral geometry contracts for extraction artifacts."""
from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
import math
from typing import Any


@dataclass(frozen=True, slots=True)
class BoundingBox:
    x0: float
    y0: float
    x1: float
    y1: float

    def __post_init__(self) -> None:
        if not all(math.isfinite(value) for value in (self.x0, self.y0, self.x1, self.y1)):
            raise ValueError("bounding box coordinates must be finite")
        if self.x0 > self.x1 or self.y0 > self.y1:
            raise ValueError("bounding box coordinates must be ordered")

    def within(self, geometry: "PageGeometry") -> bool:
        return geometry.contains(self)


@dataclass(frozen=True, slots=True)
class PageGeometry:
    width: float
    height: float
    unit: str = "pt"
    origin: str = "top-left"

    def __post_init__(self) -> None:
        if not math.isfinite(self.width) or not math.isfinite(self.height):
            raise ValueError("page dimensions must be finite")
        if self.width <= 0 or self.height <= 0:
            raise ValueError("page dimensions must be positive")
        if self.unit != "pt" or self.origin != "top-left":
            raise ValueError("geometry must use PDF points with top-left origin")

    def contains(self, box: BoundingBox) -> bool:
        return 0 <= box.x0 <= box.x1 <= self.width and 0 <= box.y0 <= box.y1 <= self.height


class PageStatus(str, Enum):
    PENDING = "PENDING"
    CLASSIFIED = "CLASSIFIED"
    EXTRACTED = "EXTRACTED"
    STRUCTURED = "STRUCTURED"
    VALIDATING = "VALIDATING"
    VALIDATED = "VALIDATED"
    RETRYING = "RETRYING"
    NEEDS_REVIEW = "NEEDS_REVIEW"


@dataclass(frozen=True, slots=True)
class LayoutBlock:
    block_id: str
    kind: str
    bbox: BoundingBox
    text: str
    extraction_method: str = "native"
    confidence: float = 1.0
    source_document_id: str | None = None
    page_number: int | None = None
    page_geometry: PageGeometry | None = None

    def __post_init__(self) -> None:
        if not self.block_id or not self.text:
            raise ValueError("layout block id and text are required")
        if not 0 <= self.confidence <= 1:
            raise ValueError("confidence must be between 0 and 1")
        if not self.source_document_id or self.page_number is None or self.page_number < 1 or self.page_geometry is None:
            raise ValueError("layout block provenance is required")


@dataclass(frozen=True, slots=True)
class LayoutRegion:
    """A vertically contiguous page area with one column structure."""

    region_id: str
    bbox: BoundingBox
    block_ids: tuple[str, ...]

    def __post_init__(self) -> None:
        if not self.region_id or not self.block_ids:
            raise ValueError("region identity and block membership are required")


@dataclass(frozen=True, slots=True)
class ColumnHypothesis:
    """One candidate column partition for a region."""

    column_count: int
    columns: tuple[BoundingBox, ...]
    score: float
    strategy: str = "x-gap"

    def __post_init__(self) -> None:
        if self.column_count < 1 or len(self.columns) != self.column_count:
            raise ValueError("column count must match column geometry")
        if not 0 <= self.score <= 1:
            raise ValueError("hypothesis score must be between 0 and 1")


@dataclass(frozen=True, slots=True)
class ColumnProfile:
    region_id: str
    candidates: tuple[ColumnHypothesis, ...]
    selected: ColumnHypothesis | None
    confidence: float
    ambiguous: bool = False
    findings: tuple[str, ...] = ()

    def __post_init__(self) -> None:
        if not self.candidates:
            raise ValueError("at least one column candidate is required")
        if not 0 <= self.confidence <= 1:
            raise ValueError("column confidence must be between 0 and 1")
        if self.selected is not None and self.selected not in self.candidates:
            raise ValueError("selected hypothesis must be one of candidates")


@dataclass(frozen=True, slots=True)
class ReadingOrderPlan:
    """Deterministic projection of page blocks and layout evidence."""

    ordered_block_ids: tuple[str, ...]
    regions: tuple[LayoutRegion, ...]
    profiles: tuple[ColumnProfile, ...]
    spanning_block_ids: tuple[str, ...] = ()
    furniture_block_ids: tuple[str, ...] = ()
    ambiguous: bool = False
    findings: tuple[str, ...] = ()

    def __post_init__(self) -> None:
        if len(set(self.ordered_block_ids)) != len(self.ordered_block_ids):
            raise ValueError("a block may occur only once in reading order")
