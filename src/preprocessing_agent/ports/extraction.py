"""Vendor-neutral ports for page rendering and targeted OCR.

Adapters return plain mappings so vendor objects never cross the application
boundary. Coordinates are PDF points, top-left origin, and OCR confidence is
kept separate from structural confidence.
"""
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping, Protocol, Sequence


class ExtractionCapabilityError(RuntimeError):
    def __init__(self, code: str, message: str = "") -> None:
        self.code = code
        super().__init__(message or code)


@dataclass(frozen=True, slots=True)
class RenderedPage:
    page_number: int
    width: float
    height: float
    image: bytes
    media_type: str = "image/png"
    pixel_width: int | None = None
    pixel_height: int | None = None
    region_origin: tuple[float, float] = (0.0, 0.0)


class PageRenderPort(Protocol):
    def render(self, source: Path, page_number: int, region: Sequence[float] | None = None) -> RenderedPage: ...

    def available(self) -> bool: ...


class OcrPort(Protocol):
    def recognize(self, rendered: RenderedPage, region: Sequence[float] | None = None) -> Sequence[Mapping[str, Any]]: ...

    def available(self) -> bool: ...


def normalize_ocr_blocks(blocks: Sequence[Mapping[str, Any]], *, page_number: int, width: float, height: float, offset: Sequence[float] | None = None) -> list[dict[str, Any]]:
    """Normalize OCR evidence while retaining word/block confidence."""
    result: list[dict[str, Any]] = []
    ox, oy = (float(offset[0]), float(offset[1])) if offset is not None else (0.0, 0.0)
    for index, item in enumerate(blocks):
        text = str(item.get("text", ""))
        bbox = item.get("bbox")
        if not text.strip() or bbox is None or len(bbox) != 4:
            continue
        local = tuple(float(value) for value in bbox)
        if not (0 <= local[0] <= local[2] <= width and 0 <= local[1] <= local[3] <= height):
            raise ExtractionCapabilityError("OCR_INVALID_GEOMETRY", "OCR bbox is outside rendered page")
        coords = (local[0] + ox, local[1] + oy, local[2] + ox, local[3] + oy)
        confidence = float(item.get("text_confidence", item.get("confidence", 0.0)))
        if not 0 <= confidence <= 1:
            raise ExtractionCapabilityError("OCR_INVALID_CONFIDENCE", "OCR confidence must be between 0 and 1")
        result.append({**dict(item), "block_id": str(item.get("block_id", f"p{page_number}-ocr-b{index}")), "text": text,
                       "bbox": coords, "extraction_method": "ocr", "text_confidence": confidence,
                       "confidence": confidence, "page_number": page_number})
    return result
