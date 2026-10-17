"""Geometry-first triage for pages that may contain tables."""
from __future__ import annotations

import hashlib
import re
from collections import Counter
from collections.abc import Mapping, Sequence

from preprocessing_agent.domain.layout import BoundingBox, PageGeometry
from preprocessing_agent.layout.analyzer import LayoutAnalyzer

TABLE_EXTRACT_THRESHOLD = 55
TABLE_SCREEN_THRESHOLD = 20
TABLE_SCREEN_DPI = 120
LOW_SCORE_AUDIT_PERCENT = 10

_VALUE_PATTERN = re.compile(r"(?:\b\d+(?:\.\d+)?\b|\b\d+d\d+\b|\b[IVXLCDM]{1,6}\b)", re.IGNORECASE)


def assess_table_candidates(*, blocks: Sequence[Mapping[str, object]], lines: Sequence[Mapping[str, object]],
                            drawings: Sequence[Mapping[str, object]], image_regions: Sequence[Sequence[float]],
                            native_candidates: Sequence[tuple[object, str]], width: float, height: float,
                            page_number: int, previous_page_had_table: bool = False) -> dict[str, object]:
    """Score layout bands, preserving explicit uncertainty and recall exceptions."""
    page = PageGeometry(width, height)
    regions = _candidate_regions(blocks, native_candidates, page)
    assessed = [_score_region(region, lines, drawings) for region in regions]
    if not assessed:
        assessed = [_score_region((0.0, 0.0, width, height), lines, drawings)]
    best = max(assessed, key=lambda item: item["score"])
    exceptions = set(best["exceptions"])
    if native_candidates:
        exceptions.add("NATIVE_TABLE_DETECTION")
    if _image_text_overlap(image_regions, lines):
        exceptions.add("IMAGE_OVERLAPS_SELECTABLE_TEXT")
    if previous_page_had_table and best["score"] >= 10:
        exceptions.add("POSSIBLE_CROSS_PAGE_TABLE")
    if _small_two_by_two_suspicion(lines):
        exceptions.add("POSSIBLE_SMALL_2X2_TABLE")
    score = max(0, min(100, int(best["score"])))
    page_fingerprint = " ".join(str(line.get("text", "")).casefold() for line in lines)
    sample_bucket = hashlib.sha256(f"table-candidate-v1:{page_number}:{page_fingerprint}".encode()).digest()[0] % 100
    audit_sample = score < TABLE_SCREEN_THRESHOLD and sample_bucket < LOW_SCORE_AUDIT_PERCENT
    route = ("extract" if score >= TABLE_EXTRACT_THRESHOLD else
             "screen" if score >= TABLE_SCREEN_THRESHOLD or exceptions or audit_sample else "skip")
    full_page = (0.0, 0.0, width, height)
    local_assessed = [item for item in assessed
                      if _intersection_over_union(full_page, item["bbox"]) < .99]
    candidate_regions = _prefer_local_regions([item for item in local_assessed
                                               if item["score"] >= TABLE_SCREEN_THRESHOLD or item["exceptions"]])
    native_boxes = []
    for candidate, _ in native_candidates:
        try:
            box = _bbox(getattr(candidate, "bbox", None))
            if _intersection_over_union(full_page, box) < .99:
                native_boxes.append(box)
        except (TypeError, ValueError, AttributeError):
            continue
    if not candidate_regions and (audit_sample or exceptions):
        candidate_regions = [max(local_assessed, key=lambda item: item["score"])] if local_assessed else []
    if score >= TABLE_EXTRACT_THRESHOLD:
        extraction_regions = _prefer_local_regions([item for item in local_assessed
                                                    if item["score"] >= TABLE_EXTRACT_THRESHOLD])
        candidate_regions = extraction_regions or candidate_regions
    else:
        extraction_regions = candidate_regions
    extraction_boxes = [item["bbox"] for item in extraction_regions]
    if route != "extract":
        extraction_boxes.extend(native_boxes)
    extraction_boxes = _merge_side_by_side_regions(_deduplicate_boxes(extraction_boxes))
    selected_boxes = [item["bbox"] for item in candidate_regions] + native_boxes
    if audit_sample:
        selected_boxes.append(full_page)
    display_boxes = selected_boxes or [best["bbox"]]
    candidate_bbox = [min(box[0] for box in display_boxes), min(box[1] for box in display_boxes),
                      max(box[2] for box in display_boxes), max(box[3] for box in display_boxes)]
    return {
        "score": score,
        "components": best["components"],
        "penalties": best["penalties"],
        "exceptions": sorted(exceptions),
        "region_count": len(regions),
        "region_bbox": [round(value, 2) for value in candidate_bbox],
        "extraction_regions": [[round(value, 2) for value in box] for box in extraction_boxes],
        "regions": [{"bbox": [round(value, 2) for value in item["bbox"]],
                     "score": item["score"], "components": item["components"],
                     "penalties": item["penalties"]} for item in assessed],
        "route": route,
        "audit_sample": audit_sample,
        "policy": {"extract_threshold": TABLE_EXTRACT_THRESHOLD,
                   "screen_threshold": TABLE_SCREEN_THRESHOLD,
                   "screen_dpi": TABLE_SCREEN_DPI,
                   "low_score_audit_percent": LOW_SCORE_AUDIT_PERCENT},
    }


def _candidate_regions(blocks: Sequence[Mapping[str, object]],
                       native_candidates: Sequence[tuple[object, str]],
                       page: PageGeometry) -> list[tuple[float, float, float, float]]:
    regions: list[tuple[float, float, float, float]] = []
    try:
        regions.extend((item.bbox.x0, item.bbox.y0, item.bbox.x1, item.bbox.y1)
                       for item in LayoutAnalyzer().analyze(blocks, page))
    except (KeyError, TypeError, ValueError, AttributeError):
        pass
    for candidate, _ in native_candidates:
        try:
            regions.append(_bbox(getattr(candidate, "bbox", None)))
        except (TypeError, ValueError, AttributeError):
            continue
    valid = [box for box in regions if len(box) == 4 and box[0] < box[2] and box[1] < box[3]]
    full_page = (0.0, 0.0, page.width, page.height)
    if not any(_intersection_over_union(full_page, box) >= .99 for box in valid):
        valid.append(full_page)
    return valid or [full_page]


def _score_region(region: tuple[float, float, float, float], lines: Sequence[Mapping[str, object]],
                  drawings: Sequence[Mapping[str, object]]) -> dict[str, object]:
    x0, y0, x1, y1 = region
    selected = [line for line in lines if _center_inside(line.get("bbox"), region)]
    rows = _rows(selected)
    multi_column_rows = [row for row in rows if len(row) >= 2]
    row_coverage = len(multi_column_rows) / max(1, len(rows))
    repeated_columns = _repeated_columns(rows)
    shared_row_count = max((count for count in repeated_columns.values()), default=0)
    alignment = (35 if shared_row_count >= 3 and row_coverage >= .7 else
                 25 if shared_row_count >= 2 and row_coverage >= .5 else
                 15 if shared_row_count >= 2 else 0)
    row_score = _row_score(multi_column_rows)
    horizontal, vertical, filled_rectangles = _shape_signals(drawings, region)
    grid_score = 20 if horizontal >= 3 and vertical >= 2 else 10 if horizontal >= 3 or vertical >= 3 else 0
    background_score = 15 if filled_rectangles >= 3 else 8 if filled_rectangles >= 2 else 0
    repeated_values = sum(bool(_VALUE_PATTERN.search(str(line.get("text", "")))) for line in selected)
    pattern_score = 5 if repeated_values >= 2 and multi_column_rows else 0
    penalties: list[tuple[str, int]] = []
    if _two_column_prose(rows):
        penalties.append(("INDEPENDENT_PROSE_COLUMNS", 20))
    if _wrapped_prose(selected):
        penalties.append(("WRAPPED_PROSE_PATTERN", 15))
    if filled_rectangles and not multi_column_rows:
        penalties.append(("COLORED_BOX_WITHOUT_CELL_STRUCTURE", 10))
    if (horizontal + vertical + filled_rectangles) >= 4 and not multi_column_rows:
        penalties.append(("DECORATIVE_SHAPES_WITHOUT_ROWS", 10))
    components = {"column_alignment": alignment, "row_structure": row_score,
                  "grid_borders": grid_score, "background_color": background_score,
                  "table_pattern": pattern_score}
    total = sum(components.values()) - sum(value for _, value in penalties)
    exceptions = []
    if len(multi_column_rows) >= 2 and len({len(row) for row in multi_column_rows}) >= 1:
        exceptions.append("POSSIBLE_SMALL_2X2_TABLE")
    if filled_rectangles >= 2 and not multi_column_rows:
        exceptions.append("REPEATED_FILLED_SHAPES_WITH_IRREGULAR_TEXT")
    return {"bbox": region, "score": max(0, min(100, total)), "components": components,
            "penalties": [{"reason": reason, "points": value} for reason, value in penalties],
            "exceptions": exceptions}


def _rows(lines: Sequence[Mapping[str, object]]) -> list[list[Mapping[str, object]]]:
    ordered = sorted((line for line in lines if _valid_bbox(line.get("bbox"))),
                     key=lambda line: (_bbox(line["bbox"])[1], _bbox(line["bbox"])[0]))
    rows: list[list[Mapping[str, object]]] = []
    for line in ordered:
        box = _bbox(line["bbox"])
        center_y = (box[1] + box[3]) / 2
        row = next((candidate for candidate in rows
                    if abs(center_y - sum((_bbox(item["bbox"])[1] + _bbox(item["bbox"])[3]) / 2
                                          for item in candidate) / len(candidate)) <= 4.0), None)
        if row is None:
            rows.append([line])
        else:
            row.append(line)
    return [sorted(row, key=lambda line: _bbox(line["bbox"])[0]) for row in rows]


def _repeated_columns(rows: Sequence[Sequence[Mapping[str, object]]]) -> Counter[float]:
    starts: list[float] = []
    for row in rows:
        for line in row:
            x0 = _bbox(line["bbox"])[0]
            match = next((value for value in starts if abs(value - x0) <= 4.0), None)
            if match is None:
                starts.append(x0)
    counts: Counter[float] = Counter()
    for row in rows:
        row_starts = {_nearest_column(_bbox(line["bbox"])[0], starts) for line in row}
        counts.update(row_starts)
    return Counter({column: count for column, count in counts.items() if count >= 2})


def _nearest_column(x: float, starts: Sequence[float]) -> float:
    return min(starts, key=lambda value: abs(value - x))


def _row_score(rows: Sequence[Sequence[Mapping[str, object]]]) -> int:
    if len(rows) >= 3:
        centers = [sum((_bbox(line["bbox"])[1] + _bbox(line["bbox"])[3]) / 2 for line in row) / len(row)
                   for row in rows]
        gaps = [right - left for left, right in zip(centers, centers[1:]) if right > left]
        if len(gaps) >= 2:
            mean = sum(gaps) / len(gaps)
            variation = (sum((gap - mean) ** 2 for gap in gaps) / len(gaps)) ** .5 / max(mean, 1)
            return 25 if variation <= .25 else 16 if variation <= .5 else 8
        return 8
    return 16 if len(rows) == 2 else 0


def _shape_signals(drawings: Sequence[Mapping[str, object]], region: tuple[float, ...]) -> tuple[int, int, int]:
    horizontal: list[float] = []
    vertical: list[float] = []
    rectangles: list[tuple[float, float, float, float]] = []
    for drawing in drawings:
        if not isinstance(drawing, Mapping):
            continue
        for item in drawing.get("items", ()):
            if not isinstance(item, (tuple, list)) or not item:
                continue
            if item[0] == "l" and len(item) >= 3:
                x_a, y_a = _point(item[1]); x_b, y_b = _point(item[2])
                if not _line_in_region(x_a, y_a, x_b, y_b, region):
                    continue
                if abs(y_a - y_b) <= 1:
                    horizontal.append(round((y_a + y_b) / 2, 1))
                elif abs(x_a - x_b) <= 1:
                    vertical.append(round((x_a + x_b) / 2, 1))
            elif item[0] == "re" and len(item) >= 2:
                box = _bbox(item[1])
                if _intersects(box, region) and drawing.get("fill") is not None:
                    rectangles.append(box)
    return _cluster_count(horizontal, 2), _cluster_count(vertical, 2), _repeated_rectangle_count(rectangles)


def _two_column_prose(rows: Sequence[Sequence[Mapping[str, object]]]) -> bool:
    paired = [row for row in rows if len(row) == 2]
    if len(paired) < 4:
        return False
    left = [str(row[0].get("text", "")).strip() for row in paired]
    right = [str(row[1].get("text", "")).strip() for row in paired]
    def prose_line_count(values: Sequence[str]) -> int:
        return sum(len(value) >= 25 and len(re.findall(r"\b\w+\b", value)) >= 5 for value in values)

    return min(prose_line_count(left), prose_line_count(right)) >= 3


def _wrapped_prose(lines: Sequence[Mapping[str, object]]) -> bool:
    if len(lines) < 4:
        return False
    ordered = sorted(lines, key=lambda line: (_bbox(line["bbox"])[1], _bbox(line["bbox"])[0]))
    continuations = 0
    for current, following in zip(ordered, ordered[1:]):
        current_text = str(current.get("text", "")).strip()
        next_text = str(following.get("text", "")).strip()
        if current_text and next_text and not re.search(r"[.!?:;)]$", current_text) and next_text[:1].islower():
            continuations += 1
    return continuations / max(len(ordered) - 1, 1) >= .5


def _small_two_by_two_suspicion(lines: Sequence[Mapping[str, object]]) -> bool:
    rows = _rows(lines)
    repeated = _repeated_columns(rows)
    return len(repeated) >= 2 and sum(1 for row in rows if len(row) >= 2) >= 2


def _image_text_overlap(image_regions: Sequence[Sequence[float]], lines: Sequence[Mapping[str, object]]) -> bool:
    return any(_intersects(_bbox(image), _bbox(line.get("bbox")))
               for image in image_regions for line in lines
               if _valid_bbox(image) and _valid_bbox(line.get("bbox")))


def _center_inside(box: object, region: tuple[float, ...]) -> bool:
    if not _valid_bbox(box):
        return False
    x0, y0, x1, y1 = _bbox(box)
    return region[0] <= (x0 + x1) / 2 <= region[2] and region[1] <= (y0 + y1) / 2 <= region[3]


def _valid_bbox(box: object) -> bool:
    try:
        values = _bbox(box)
        return len(values) == 4 and values[0] <= values[2] and values[1] <= values[3]
    except (TypeError, ValueError, AttributeError):
        return False


def _bbox(value: object) -> tuple[float, float, float, float]:
    if hasattr(value, "x0"):
        return tuple(float(getattr(value, key)) for key in ("x0", "y0", "x1", "y1"))
    return tuple(float(item) for item in value)  # type: ignore[arg-type,return-value]


def _point(value: object) -> tuple[float, float]:
    if hasattr(value, "x"):
        return float(value.x), float(value.y)
    return float(value[0]), float(value[1])  # type: ignore[index]


def _line_in_region(x0: float, y0: float, x1: float, y1: float, region: tuple[float, ...]) -> bool:
    return region[0] <= x0 <= region[2] and region[1] <= y0 <= region[3] and region[0] <= x1 <= region[2] and region[1] <= y1 <= region[3]


def _intersects(left: tuple[float, ...], right: tuple[float, ...]) -> bool:
    return min(left[2], right[2]) >= max(left[0], right[0]) and min(left[3], right[3]) >= max(left[1], right[1])


def _cluster_count(values: Sequence[float], tolerance: float) -> int:
    clusters: list[float] = []
    for value in sorted(values):
        if not clusters or value - clusters[-1] > tolerance:
            clusters.append(value)
    return len(clusters)


def _repeated_rectangle_count(rectangles: Sequence[tuple[float, ...]]) -> int:
    aligned = Counter()
    for index, box in enumerate(rectangles):
        for other_index, other in enumerate(rectangles[index + 1:], index + 1):
            same_row = abs(box[1] - other[1]) <= 2 and abs(box[3] - other[3]) <= 2
            same_column = abs(box[0] - other[0]) <= 2 and abs(box[2] - other[2]) <= 2
            if same_row or same_column:
                aligned[index] += 1
                aligned[other_index] += 1
    return max(aligned.values(), default=0) + 1 if aligned else 0


def _deduplicate_boxes(boxes: Sequence[tuple[float, ...]]) -> list[tuple[float, ...]]:
    result: list[tuple[float, ...]] = []
    for box in boxes:
        if not any(_intersection_over_union(box, prior) >= .9 for prior in result):
            result.append(box)
    return result


def _merge_side_by_side_regions(boxes: Sequence[tuple[float, ...]]) -> list[tuple[float, ...]]:
    merged = list(boxes)
    changed = True
    while changed:
        changed = False
        for left_index, left in enumerate(merged):
            for right_index in range(left_index + 1, len(merged)):
                right = merged[right_index]
                vertical_overlap = max(0.0, min(left[3], right[3]) - max(left[1], right[1]))
                smaller_height = min(left[3] - left[1], right[3] - right[1])
                larger_height = max(left[3] - left[1], right[3] - right[1])
                horizontal_gap = max(0.0, max(left[0], right[0]) - min(left[2], right[2]))
                if (smaller_height / max(larger_height, 1.0) >= .75
                        and vertical_overlap / max(smaller_height, 1.0) >= .8
                        and horizontal_gap <= max(8.0, smaller_height * .5)):
                    merged[left_index] = (min(left[0], right[0]), min(left[1], right[1]),
                                          max(left[2], right[2]), max(left[3], right[3]))
                    merged.pop(right_index)
                    changed = True
                    break
            if changed:
                break
    return merged


def _prefer_local_regions(regions: list[dict[str, object]]) -> list[dict[str, object]]:
    selected = []
    for region in regions:
        box = region["bbox"]
        area = max((box[2] - box[0]) * (box[3] - box[1]), 1.0)
        has_similar_local = any(
            other is not region
            and int(other["score"]) >= int(region["score"]) - 15
            and (other["bbox"][2] - other["bbox"][0]) * (other["bbox"][3] - other["bbox"][1]) < area * .7
            and _contains_box(box, other["bbox"])
            for other in regions
        )
        if not has_similar_local:
            selected.append(region)
    return selected or regions


def _contains_box(outer: tuple[float, ...], inner: tuple[float, ...]) -> bool:
    return (outer[0] <= inner[0] + 4 and outer[1] <= inner[1] + 4
            and outer[2] >= inner[2] - 4 and outer[3] >= inner[3] - 4)


def _intersection_over_union(left: tuple[float, ...], right: tuple[float, ...]) -> float:
    intersection = max(0.0, min(left[2], right[2]) - max(left[0], right[0])) * max(
        0.0, min(left[3], right[3]) - max(left[1], right[1]))
    left_area = max(0.0, left[2] - left[0]) * max(0.0, left[3] - left[1])
    right_area = max(0.0, right[2] - right[0]) * max(0.0, right[3] - right[1])
    return intersection / max(left_area + right_area - intersection, 1.0)
