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
