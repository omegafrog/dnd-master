"""Deterministic, geometry-preserving heading and table structures.

This module deliberately consumes canonical layout blocks and does not perform
OCR or page validation. Ambiguous structure is represented as evidence for the
publication gate owned by a later plan.
"""
from __future__ import annotations

from dataclasses import dataclass
from collections import defaultdict
import re
from typing import Iterable, Mapping, Protocol

from preprocessing_agent.domain.layout import BoundingBox
from .detector import HeadingDetector


class StructuredBlock(Protocol):
    block_id: str
    bbox: BoundingBox | tuple[float, float, float, float]


StructuredValue = StructuredBlock | Mapping[str, object]


def _get(value: StructuredValue, name: str, default: object = None) -> object:
    return getattr(value, name, default) if not isinstance(value, dict) else value.get(name, default)


def _bbox(value: StructuredValue) -> BoundingBox:
    raw = _get(value, "bbox")
    if raw is None:
        raise ValueError("structured layout requires bbox")
    return raw if isinstance(raw, BoundingBox) else BoundingBox(*(float(item) for item in raw))


def _id(value: StructuredValue) -> str:
    result = _get(value, "block_id")
    if not result:
        raise ValueError("structured layout requires block_id")
    return str(result)


def _text(value: StructuredValue) -> str:
    return str(_get(value, "source_text", _get(value, "text", "")))


@dataclass(frozen=True, slots=True)
class HeadingAssociation:
    heading_block_id: str
    level: int
    associated_block_ids: tuple[str, ...]
    evidence: tuple[str, ...]
    confidence: float
    ambiguous: bool = False
    findings: tuple[str, ...] = ()
    heading_text: str | None = None
    source_char_start: int | None = None
    source_char_end: int | None = None
    hierarchy_resolved: bool = False
    hierarchy_evidence: tuple[str, ...] = ()
    parent_heading_block_id: str | None = None


@dataclass(frozen=True, slots=True)
class HeadingCandidate:
    block_id: str
    line_index: int
    span_indices: tuple[int, ...]
    source_char_start: int
    source_char_end: int
    text: str
    bbox: tuple[float, float, float, float] | None
    font_size: float | None
    font_weight: str | None
    confidence: float
    accepted: bool
    reason: str
    evidence: tuple[str, ...] = ()


class HeadingAssociator:
    """Associates a heading only with later blocks in its local x-region."""

    def __init__(self, detector: HeadingDetector | None = None) -> None:
        self.detector = detector or HeadingDetector()

    def associate(self, blocks: Iterable[StructuredValue], reading_plan: object | None = None,
                  reference_font_size: float | None = None) -> tuple[HeadingAssociation, ...]:
        associations, _ = self.analyze(blocks, reading_plan, reference_font_size)
        return associations

    def analyze(self, blocks: Iterable[StructuredValue], reading_plan: object | None = None,
                reference_font_size: float | None = None) -> tuple[tuple[HeadingAssociation, ...], tuple[HeadingCandidate, ...]]:
        values = tuple(blocks)
        candidates: list[HeadingCandidate] = []
        accepted_by_range: dict[tuple[str, int, int], HeadingCandidate] = {}
        for block in values:
            if str(_get(block, "table_id", "")) or str(_get(block, "kind", "")).casefold() in {"table", "table_cell", "cell"}:
                continue
            for line_index, line in enumerate(_candidate_lines(block)):
                text = line["text"].strip()
                if not text:
                    continue
                font_size = line.get("font_size")
                font_weight = line.get("font_weight")
                isolated = bool(line.get("spatially_isolated"))
                relative_size = bool(font_size is not None and reference_font_size
                                     and float(font_size) >= reference_font_size * 1.15)
                bold = bool(font_weight and str(font_weight).casefold() in {"bold", "semibold", "700", "bolditalic"})
                numbered = bool(re.match(r"^(?:\d+(?:\.\d+)*\.?|[IVX]+\.)\s+", text, re.I))
                if len(text) > 160 and not (relative_size or bold or isolated or numbered):
                    continue
                parsed = _as_parsed_block({"block_id": _id(block), "text": text,
                    "bbox": line.get("bbox") or _bbox(block), "font_size": font_size,
                    "font_weight": font_weight})
                decision = self.detector.detect(parsed, reference_font_size=reference_font_size,
                                                spatially_isolated=isolated)
                char_start = int(line.get("char_start", 0))
                char_end = int(line.get("char_end", char_start + len(text)))
                evidence = tuple(item for item, enabled in (
                    ("numbered", decision.feature.numbered),
                    ("relative_font_size", relative_size),
                    ("bold_style", bold),
                    ("spatial_separation", isolated),
                ) if enabled)
                candidate = HeadingCandidate(_id(block), line_index,
                    tuple(int(value) for value in line.get("span_indices", ())), char_start, char_end,
                    text, _box_tuple(line.get("bbox")), font_size,
                    str(font_weight) if font_weight else None, decision.confidence,
                    decision.is_heading, decision.reason, evidence)
                candidates.append(candidate)
                if decision.is_heading:
                    key = (_id(block), char_start, char_end)
                    current = accepted_by_range.get(key)
                    if current is None or candidate.confidence > current.confidence:
                        accepted_by_range[key] = candidate

        by_id = {_id(value): value for value in values}
        planned_ids = tuple(getattr(reading_plan, "ordered_block_ids", ())) if reading_plan is not None else ()
        ordered_values = tuple(by_id[item] for item in planned_ids if item in by_id)
        ordered_ids = {item for item in planned_ids if item in by_id}
        ordered_values += tuple(value for value in values if _id(value) not in ordered_ids)
        block_order = {_id(value): index for index, value in enumerate(ordered_values)}
        result: list[HeadingAssociation] = []
        accepted = sorted(accepted_by_range.values(), key=lambda item: (
            block_order[item.block_id], item.line_index))
        for position, candidate_heading in enumerate(accepted):
            heading = by_id[candidate_heading.block_id]
            heading_box = _candidate_bbox(candidate_heading, heading)
            members: list[str] = []
            evidence: set[str] = set()
            next_heading = accepted[position + 1] if position + 1 < len(accepted) else None
            start = block_order[candidate_heading.block_id] + 1
            stop = block_order[next_heading.block_id] if next_heading is not None else len(ordered_values)
            if next_heading is not None and next_heading.block_id == candidate_heading.block_id:
                stop = block_order[candidate_heading.block_id] + 1
            for content in ordered_values[start:stop]:
                candidate_box = _bbox(content)
                if candidate_box.y0 < heading_box.y1:
                    continue
                if _spans(heading_box, candidate_box, values):
                    members.append(_id(content))
                    evidence.add("spanning_content")
                elif _same_column(heading_box, candidate_box, reading_plan):
                    members.append(_id(content))
                    evidence.add("same_column")
            decision = self.detector.detect(_as_parsed_block({"block_id": _id(heading),
                "text": candidate_heading.text, "bbox": heading_box,
                "font_size": candidate_heading.font_size, "font_weight": candidate_heading.font_weight}),
                reference_font_size=reference_font_size,
                spatially_isolated="spatial_separation" in candidate_heading.evidence)
            result.append(HeadingAssociation(_id(heading), decision.level or 1, tuple(dict.fromkeys(members)),
                tuple(sorted(evidence | set(candidate_heading.evidence))), candidate_heading.confidence,
                False, (), candidate_heading.text, candidate_heading.source_char_start,
                candidate_heading.source_char_end))
        return tuple(result), tuple(candidates)


def document_reference_font_size(pages: Iterable[Mapping[str, object]]) -> float | None:
    """Return a text-length-weighted document font size, excluding table cells."""
    sizes: list[tuple[float, int]] = []
    for page in pages:
        if page.get("page_classification") == "form":
            continue
        blocks = page.get("blocks", ())
        if not isinstance(blocks, (tuple, list)):
            continue
        geometry = page.get("geometry", {})
        try:
            page_height = float(geometry.get("height")) if isinstance(geometry, Mapping) else 0.0
        except (TypeError, ValueError):
            page_height = 0.0
        if page_height <= 0:
            page_height = max((float(block.get("bbox", (0, 0, 0, 0))[3]) for block in blocks
                               if isinstance(block, Mapping) and isinstance(block.get("bbox"), (tuple, list))
                               and len(block["bbox"]) == 4), default=0.0)
        for block in blocks:
            if not isinstance(block, Mapping) or block.get("table_id") or str(block.get("kind", "")).casefold() in {"table", "table_cell", "cell"}:
                continue
            box = block.get("bbox")
            if page_height > 0 and isinstance(box, (tuple, list)) and len(box) == 4:
                if float(box[1]) <= page_height * 0.06 or float(box[3]) >= page_height * 0.94:
                    continue
            spans = [span for line in block.get("lines", ()) if isinstance(line, Mapping)
                     for span in line.get("spans", ()) if isinstance(span, Mapping)]
            if not spans:
                spans = [block]
            for span in spans:
                try:
                    size = float(span.get("font_size", span.get("size", block.get("font_size", block.get("size")))))
                except (TypeError, ValueError):
                    continue
                text_length = max(1, len(str(span.get("text", block.get("text", ""))).strip()))
                sizes.append((size, text_length))
    if not sizes:
        return None
    ordered = sorted(sizes)
    midpoint = sum(weight for _, weight in ordered) // 2
    cumulative = 0
    for size, weight in ordered:
        cumulative += weight
        if cumulative > midpoint:
            return size
    return ordered[-1][0]


def _candidate_lines(block: StructuredValue) -> tuple[dict[str, object], ...]:
    raw_lines = _get(block, "lines", ())
    text = _text(block)
    if not isinstance(raw_lines, (tuple, list)) or not raw_lines:
        split_lines = text.splitlines()
        if len(split_lines) > 1:
            block_box = _bbox(block)
            line_height = (block_box.y1 - block_box.y0) / len(split_lines)
            result = []
            cursor = 0
            for index, line_text in enumerate(split_lines):
                start = text.find(line_text, cursor)
                start = cursor if start < 0 else start
                end = start + len(line_text)
                result.append({"text": line_text, "bbox": (block_box.x0, block_box.y0 + index * line_height,
                    block_box.x1, block_box.y0 + (index + 1) * line_height),
                    "font_size": _get(block, "font_size", _get(block, "size")),
                    "font_weight": _get(block, "font_weight", _get(block, "weight", _get(block, "font"))),
                    "char_start": start, "char_end": end, "span_indices": (),
                    "spatially_isolated": False})
                cursor = end
            return tuple(result)
        return ({"text": text, "bbox": _bbox(block), "font_size": _get(block, "font_size", _get(block, "size")),
                 "font_weight": _get(block, "font_weight", _get(block, "weight", _get(block, "font"))),
                 "char_start": 0, "char_end": len(text), "span_indices": (),
                 "spatially_isolated": False},)
    result: list[dict[str, object]] = []
    cursor = 0
    cleaned = []
    for index, line in enumerate(raw_lines):
        if not isinstance(line, Mapping):
            continue
        line_text = str(line.get("text", ""))
        if not line_text:
            spans = line.get("spans", ())
            line_text = " ".join(str(span.get("text", "")) for span in spans if isinstance(span, Mapping)).strip()
        if not line_text:
            continue
        start = text.find(line_text, cursor)
        if start < 0:
            start = cursor
        end = min(len(text), start + len(line_text))
        spans = [span for span in line.get("spans", ()) if isinstance(span, Mapping)]
        font_sizes = [float(span.get("font_size", span.get("size"))) for span in spans
                      if span.get("font_size", span.get("size")) is not None]
        fonts = [str(span.get("font_weight", span.get("font", ""))) for span in spans]
        font = "bold" if any("bold" in value.casefold() for value in fonts) else (fonts[0] if fonts else _get(block, "font_weight", _get(block, "font")))
        box = line.get("bbox")
        cleaned.append({"text": line_text, "bbox": box if box and len(box) == 4 else _bbox(block),
            "font_size": sorted(font_sizes)[len(font_sizes) // 2] if font_sizes else _get(block, "font_size", _get(block, "size")),
            "font_weight": font, "char_start": start, "char_end": end,
            "span_indices": tuple(range(len(spans))), "line_index": index})
        cursor = end
    for index, item in enumerate(cleaned):
        box = item.get("bbox")
        next_box = cleaned[index + 1].get("bbox") if index + 1 < len(cleaned) else None
        previous_box = cleaned[index - 1].get("bbox") if index else None
        box_values = _box_tuple(box)
        next_values = _box_tuple(next_box)
        previous_values = _box_tuple(previous_box)
        height = max(1.0, box_values[3] - box_values[1]) if box_values else 1.0
        gap_after = next_values[1] - box_values[3] if box_values and next_values else 0.0
        gap_before = box_values[1] - previous_values[3] if box_values and previous_values else 0.0
        isolated = len(str(item["text"])) <= 80 and max(gap_after, gap_before) >= height * 0.55
        result.append({**item, "spatially_isolated": isolated})
    return tuple(result) or ({"text": text, "bbox": _bbox(block), "char_start": 0,
        "char_end": len(text), "font_size": _get(block, "font_size"), "font_weight": _get(block, "font_weight"),
        "span_indices": (), "spatially_isolated": False},)


def _candidate_bbox(candidate: HeadingCandidate, block: StructuredValue) -> BoundingBox:
    return _bbox({"bbox": candidate.bbox}) if candidate.bbox is not None else _bbox(block)


def _box_tuple(value: object) -> tuple[float, float, float, float] | None:
    if value is None:
        return None
    if isinstance(value, BoundingBox):
        return value.x0, value.y0, value.x1, value.y1
    values = tuple(float(item) for item in value)  # type: ignore[arg-type]
    return values if len(values) == 4 else None


def _same_column(heading: BoundingBox, candidate: BoundingBox, plan: object | None) -> bool:
    if plan is not None:
        center = (candidate.x0 + candidate.x1) / 2
        for profile in getattr(plan, "profiles", ()):
            selected = getattr(profile, "selected", None)
            if selected is None:
                continue
            columns = getattr(selected, "columns", ())
            heading_column = next((column for column in columns if column.x0 <= (heading.x0 + heading.x1) / 2 <= column.x1), None)
            if heading_column is not None:
                return heading_column.x0 <= center <= heading_column.x1
    overlap = max(0.0, min(heading.x1, candidate.x1) - max(heading.x0, candidate.x0))
    return overlap / max(min(heading.x1 - heading.x0, candidate.x1 - candidate.x0), 1.0) >= 0.5


def _spans(heading: BoundingBox, candidate: BoundingBox, values: tuple[StructuredValue, ...]) -> bool:
    left = min(_bbox(value).x0 for value in values)
    right = max(_bbox(value).x1 for value in values)
    width = max(right - left, 1.0)
    # A full-width block below a local heading is a valid spanning section;
    # the heading itself need not be full-width.
    return (candidate.x0 <= left + width * .08 and candidate.x1 >= right - width * .08 and
            candidate.y0 >= heading.y1)


def _as_parsed_block(value: StructuredValue):
    from preprocessing_agent.domain import ParsedBlock, SourceSpan
    if hasattr(value, "source_span"):
        return value
    box = _get(value, "bbox")
    if isinstance(box, BoundingBox):
        box = (box.x0, box.y0, box.x1, box.y1)
    return ParsedBlock(_id(value), _text(value) or " ", SourceSpan(1), box, _get(value, "font_size", _get(value, "size")), _get(value, "font_weight", _get(value, "weight", _get(value, "font"))))


@dataclass(frozen=True, slots=True)
class TableCell:
    cell_id: str
    row_index: int
    column_index: int
    text: str
    bbox: BoundingBox
    row_span: int = 1
    column_span: int = 1
    merged: bool = False
    uncertain: bool = False
    source_block_ids: tuple[str, ...] = ()


@dataclass(frozen=True, slots=True)
class TableRow:
    row_index: int
    cells: tuple[TableCell, ...]
    header: bool = False


@dataclass(frozen=True, slots=True)
class TableStructure:
    table_id: str
    bbox: BoundingBox
    header_rows: tuple[TableRow, ...]
    rows: tuple[TableRow, ...]
    cells: tuple[TableCell, ...]
    merged_cell_ids: tuple[str, ...] = ()
    uncertain_cell_ids: tuple[str, ...] = ()
    findings: tuple[str, ...] = ()
    confidence: float = 1.0


class TableStructureDetector:
    """Builds explicit row/header/cell structures without prose flattening."""

    def detect(self, blocks: Iterable[StructuredValue]) -> tuple[TableStructure, ...]:
        groups: dict[str, list[StructuredValue]] = defaultdict(list)
        seen: dict[str, StructuredValue] = {}
        for value in blocks:
            block_id = _id(value)
            previous = seen.get(block_id)
            if previous is not None:
                # Native table projections can overlap a text block returned
                # by get_text(). Keep exactly one deterministic source block,
                # preferring the explicitly structured table-cell record.
                previous_kind = str(_get(previous, "kind", "")).casefold()
                current_kind = str(_get(value, "kind", "")).casefold()
                if previous_kind in {"table", "table_cell", "cell"} or current_kind not in {"table", "table_cell", "cell"}:
                    continue
            seen[block_id] = value
        for value in seen.values():
            table_id = _get(value, "table_id")
            kind = str(_get(value, "kind", ""))
            if table_id or kind.casefold() in {"table", "table_cell", "cell"}:
                groups[str(table_id or "table-1")].append(value)
        return tuple(self._build(table_id, values) for table_id, values in sorted(groups.items()))

    def _build(self, table_id: str, values: list[StructuredValue]) -> TableStructure:
        values.sort(key=lambda value: (_row(value), _bbox(value).y0, _bbox(value).x0, _id(value)))
        min_x = min(_bbox(value).x0 for value in values); min_y = min(_bbox(value).y0 for value in values)
        max_x = max(_bbox(value).x1 for value in values); max_y = max(_bbox(value).y1 for value in values)
        bbox = BoundingBox(min_x, min_y, max_x, max_y)
        explicit_cols = max((_column(value) for value in values), default=0) + 1
        inferred_cols = _infer_columns(values)
        columns = max(explicit_cols, inferred_cols)
        rows: dict[int, list[TableCell]] = defaultdict(list)
        findings: set[str] = set()
        for value in values:
            row = _row(value); column = _column(value)
            span = max(1, int(_get(value, "column_span", 1)))
            merged = bool(_get(value, "merged", False) or span > 1)
            uncertain = bool(_get(value, "uncertain", False))
            cell = TableCell(_id(value), row, column, _text(value), _bbox(value), max(1, int(_get(value, "row_span", 1))), span, merged, uncertain, (_id(value),))
            rows[row].append(cell)
        row_values: list[TableRow] = []
        for row_index in sorted(rows):
            cells = tuple(sorted(rows[row_index], key=lambda cell: (cell.column_index, cell.bbox.x0, cell.cell_id)))
            header = any(bool(_get(value, "is_header", False)) for value in values if _row(value) == row_index)
            covered = sum(cell.column_span for cell in cells)
            positions = [position for cell in cells for position in range(cell.column_index, cell.column_index + cell.column_span)]
            if covered != columns or len(positions) != len(set(positions)) or set(positions) != set(range(columns)):
                findings.add("IRREGULAR_TABLE")
                cells = tuple(_mark_uncertain(cell) if not cell.uncertain else cell for cell in cells)
            row_values.append(TableRow(row_index, cells, header))
        headers = tuple(row for row in row_values if row.header)
        data_rows = tuple(row for row in row_values if not row.header)
        cells = tuple(cell for row in row_values for cell in row.cells)
        uncertain = tuple(cell.cell_id for cell in cells if cell.uncertain)
        merged = tuple(cell.cell_id for cell in cells if cell.merged)
        confidence = 0.6 if findings else 1.0
        return TableStructure(table_id, bbox, headers, data_rows, cells, merged, uncertain, tuple(sorted(findings)), confidence)


def _row(value: StructuredValue) -> int:
    explicit = _get(value, "row")
    return int(explicit) if explicit is not None else 0


def _column(value: StructuredValue) -> int:
    explicit = _get(value, "column")
    return int(explicit) if explicit is not None else 0


def _infer_columns(values: list[StructuredValue]) -> int:
    first_y = min(_bbox(value).y0 for value in values)
    return len([value for value in values if abs(_bbox(value).y0 - first_y) < 1e-6])


def _mark_uncertain(cell: TableCell) -> TableCell:
    return TableCell(cell.cell_id, cell.row_index, cell.column_index, cell.text, cell.bbox, cell.row_span, cell.column_span, cell.merged, True, cell.source_block_ids)
