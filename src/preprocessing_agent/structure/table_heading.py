"""Deterministic, geometry-preserving heading and table structures.

This module deliberately consumes canonical layout blocks and does not perform
OCR or page validation. Ambiguous structure is represented as evidence for the
publication gate owned by a later plan.
"""
from __future__ import annotations

from dataclasses import dataclass
from collections import defaultdict
from typing import Any, Iterable

from preprocessing_agent.domain.layout import BoundingBox
from .detector import HeadingDetector


def _get(value: Any, name: str, default: Any = None) -> Any:
    return getattr(value, name, default) if not isinstance(value, dict) else value.get(name, default)


def _bbox(value: Any) -> BoundingBox:
    raw = _get(value, "bbox")
    if raw is None:
        raise ValueError("structured layout requires bbox")
    return raw if isinstance(raw, BoundingBox) else BoundingBox(*(float(item) for item in raw))


def _id(value: Any) -> str:
    result = _get(value, "block_id")
    if not result:
        raise ValueError("structured layout requires block_id")
    return str(result)


def _text(value: Any) -> str:
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


class HeadingAssociator:
    """Associates a heading only with later blocks in its local x-region."""

    def __init__(self, detector: HeadingDetector | None = None) -> None:
        self.detector = detector or HeadingDetector()

    def associate(self, blocks: Iterable[Any], reading_plan: Any | None = None) -> tuple[HeadingAssociation, ...]:
        values = tuple(blocks)
        result: list[HeadingAssociation] = []
        for index, heading in enumerate(values):
            decision = self.detector.detect(_as_parsed_block(heading))
            if not decision.is_heading:
                continue
            heading_box = _bbox(heading)
            members: list[str] = []
            evidence: set[str] = set()
            for candidate in values[index + 1 :]:
                candidate_box = _bbox(candidate)
                if candidate_box.y0 < heading_box.y1:
                    continue
                if self.detector.detect(_as_parsed_block(candidate)).is_heading:
                    break
                if _spans(heading_box, candidate_box, values):
                    members.append(_id(candidate))
                    evidence.add("spanning_content")
                elif _same_column(heading_box, candidate_box, reading_plan):
                    members.append(_id(candidate))
                    evidence.add("same_column")
            result.append(HeadingAssociation(_id(heading), decision.level or 1, tuple(members), tuple(sorted(evidence)), decision.confidence))
        return tuple(result)


def _same_column(heading: BoundingBox, candidate: BoundingBox, plan: Any | None) -> bool:
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


def _spans(heading: BoundingBox, candidate: BoundingBox, values: tuple[Any, ...]) -> bool:
    left = min(_bbox(value).x0 for value in values)
    right = max(_bbox(value).x1 for value in values)
    width = max(right - left, 1.0)
    # A full-width block below a local heading is a valid spanning section;
    # the heading itself need not be full-width.
    return (candidate.x0 <= left + width * .08 and candidate.x1 >= right - width * .08 and
            candidate.y0 >= heading.y1)


def _as_parsed_block(value: Any):
    from preprocessing_agent.domain import ParsedBlock, SourceSpan
    if hasattr(value, "source_span"):
        return value
    return ParsedBlock(_id(value), _text(value) or " ", SourceSpan(1), _get(value, "bbox"), _get(value, "font_size", _get(value, "size")), _get(value, "font_weight", _get(value, "weight", _get(value, "font"))))


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

    def detect(self, blocks: Iterable[Any]) -> tuple[TableStructure, ...]:
        groups: dict[str, list[Any]] = defaultdict(list)
        for value in blocks:
            table_id = _get(value, "table_id")
            kind = str(_get(value, "kind", ""))
            if table_id or kind.casefold() in {"table", "table_cell", "cell"}:
                groups[str(table_id or "table-1")].append(value)
        return tuple(self._build(table_id, values) for table_id, values in sorted(groups.items()))

    def _build(self, table_id: str, values: list[Any]) -> TableStructure:
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
            header = any(bool(_get(value, "is_header", False)) for value in values if _row(value) == row_index) or row_index == min(rows)
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


def _row(value: Any) -> int:
    explicit = _get(value, "row")
    return int(explicit) if explicit is not None else 0


def _column(value: Any) -> int:
    explicit = _get(value, "column")
    return int(explicit) if explicit is not None else 0


def _infer_columns(values: list[Any]) -> int:
    first_y = min(_bbox(value).y0 for value in values)
    return len([value for value in values if abs(_bbox(value).y0 - first_y) < 1e-6])


def _mark_uncertain(cell: TableCell) -> TableCell:
    return TableCell(cell.cell_id, cell.row_index, cell.column_index, cell.text, cell.bbox, cell.row_span, cell.column_span, cell.merged, True, cell.source_block_ids)
