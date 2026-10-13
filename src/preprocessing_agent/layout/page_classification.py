"""Geometry-only page classification for sparse forms."""
from __future__ import annotations


def form_geometry_counts(drawings, text_line_count: int) -> dict[str, int]:
    lines = [item for drawing in drawings for item in drawing.get("items", ()) if item[0] == "l"]
    return {
        "drawing_count": len(drawings),
        "aligned_line_count": sum(1 for item in lines
                                   if abs(item[1].y - item[2].y) < 1 or abs(item[1].x - item[2].x) < 1),
        "long_horizontal_line_count": sum(1 for item in lines
                                           if abs(item[1].y - item[2].y) < 1
                                           and abs(item[1].x - item[2].x) >= 80),
        "text_line_count": text_line_count,
    }


def is_form_like(*, drawing_count: int, aligned_line_count: int,
                 long_horizontal_line_count: int, text_line_count: int) -> bool:
    """Identify repeated writing rules and field geometry without reading page text."""
    return (drawing_count >= 100 and aligned_line_count >= 100
            and long_horizontal_line_count >= 40
            and long_horizontal_line_count >= text_line_count * 1.5)


def form_content_state(*, populated_field_count: int, image_region_count: int,
                       text_block_count: int = 0) -> str:
    """Classify searchable content separately from the page's form appearance."""
    if populated_field_count > 0:
        return "filled"
    if image_region_count > 0 or text_block_count > 0:
        # Flattened form values and handwriting cannot be distinguished safely from labels.
        return "unknown"
    return "blank"
