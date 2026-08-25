"""Vendor-neutral geometry contracts for extraction artifacts."""
from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
import math


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
