"""Reading-order projection for prose regions and explicit table zones."""
from __future__ import annotations

from collections import defaultdict
from collections.abc import Mapping, Sequence
import logging
import os
import time
from typing import Any

from preprocessing_agent.domain.layout import BoundingBox, LayoutRegion, PageGeometry, ReadingOrderPlan, TableZone
from .analyzer import LayoutAnalyzer, _box, _id

_LOGGER = logging.getLogger(__name__)


def _value(block: Any, name: str, default: Any = None) -> Any:
    if hasattr(block, name):
        return getattr(block, name)
    return block.get(name, default) if isinstance(block, Mapping) else default


def _table_key(block: Any) -> str | None:
    table_id = _value(block, "table_id")
    kind = str(_value(block, "kind", "")).casefold()
    if table_id:
        return f"table:{table_id}"
    return "table:table-1" if kind in {"table", "table_cell", "cell"} else None


class ReadingOrderPlanner:
    def __init__(self, analyzer: LayoutAnalyzer | None = None) -> None:
        self.analyzer = analyzer or LayoutAnalyzer()

    def plan(self, blocks: Sequence[Any], page_geometry: Any | None = None,
             selections: dict[str, int] | None = None) -> ReadingOrderPlan:
        if not blocks:
            return ReadingOrderPlan((), (), ())
        started = time.monotonic()
        entries = [(_id(block, i), _box(block), block, _table_key(block)) for i, block in enumerate(blocks)]
        groups: dict[str, list[tuple[str, BoundingBox, Any, str | None]]] = defaultdict(list)
        prose = []
        for entry in entries:
            (groups[entry[3]] if entry[3] else prose).append(entry)
        table_zones = []
        for key, members in groups.items():
            members.sort(key=lambda item: (
                _value(item[2], "row") is None,
                _value(item[2], "row") if _value(item[2], "row") is not None else item[1].y0,
                _value(item[2], "column") is None,
                _value(item[2], "column") if _value(item[2], "column") is not None else item[1].x0,
                item[0],
            ))
            bbox = BoundingBox(min(x[1].x0 for x in members), min(x[1].y0 for x in members),
                               max(x[1].x1 for x in members), max(x[1].y1 for x in members))
            table_zones.append(TableZone(key.removeprefix("table:"), bbox, tuple(x[0] for x in members)))
        table_zones.sort(key=lambda zone: (zone.bbox.y0, zone.bbox.x0, zone.zone_id))

        selections = selections or {}
        prose_blocks = [item[2] for item in prose]
        regions = self.analyzer.analyze(prose_blocks, page_geometry)
        regions = tuple(LayoutRegion(f"region-{index}", region.bbox, region.block_ids)
                        for index, region in enumerate(regions, 1))
        profiles = tuple(self.analyzer.profile(region, prose_blocks,
                                               selected_candidate=selections.get(region.region_id))
                         for region in regions)
        by_id = {_id(block, i): block for i, block in enumerate(prose_blocks)}
        all_prose_boxes = [_box(item[2]) for item in prose]
        content_left = min((box.x0 for box in all_prose_boxes), default=0.0)
        content_right = max((box.x1 for box in all_prose_boxes), default=1.0)
        content_width = max(content_right - content_left, 1.0)
        ordered: list[tuple[int, int, float, str]] = []
        spanning: list[str] = []
        furniture: list[str] = []
        multi_region = any(profile.selected is not None and profile.selected.column_count > 1 for profile in profiles)
        table_regions = {table.zone_id: next((index for index, region in enumerate(regions)
                                              if region.bbox.y0 <= (table.bbox.y0 + table.bbox.y1) / 2 <= region.bbox.y1), None)
                         for table in table_zones}

        for region_index, (region, profile) in enumerate(zip(regions, profiles)):
            events: list[tuple[int, float, str, TableZone | None]] = []
            if profile.selected is None:
                events.extend((0, _box(by_id[block_id]).y0, block_id, None) for block_id in region.block_ids)
            else:
                columns = profile.selected.columns
                for block_id in region.block_ids:
                    box = _box(by_id[block_id])
                    column = next((index for index, column_box in enumerate(columns)
                                   if box.x0 >= column_box.x0 and box.x1 <= column_box.x1), 0)
                    kind = str(_value(by_id[block_id], "kind", "")).casefold()
                    font_size = _value(by_id[block_id], "font_size")
                    is_furniture = (kind in {"header", "footer", "repeated-header", "repeated-footer"}
                                    or (font_size is not None and float(font_size) <= 9 and (box.y0 < 60 or box.y1 > 720)))
                    is_spanning = (multi_region and box.x0 <= content_left + content_width * .08
                                   and box.x1 >= content_right - content_width * .08
                                   and (box.x1 - box.x0) / content_width >= .82)
                    if is_furniture:
                        furniture.append(block_id)
                    else:
                        if is_spanning:
                            spanning.append(block_id)
                        events.append((-1 if is_spanning else column, box.y0, block_id, None))
            for table in table_zones:
                if table_regions[table.zone_id] == region_index:
                    column = 0
                    if profile.selected is not None:
                        columns = profile.selected.columns
                        overlaps = [max(0.0, min(table.bbox.x1, box.x1) - max(table.bbox.x0, box.x0)) for box in columns]
                        column = max(range(len(overlaps)), key=overlaps.__getitem__)
                        if sum(value > 0 for value in overlaps) > 1:
                            column = -1
                    events.append((column, table.bbox.y0, table.zone_id, table))
            for column, y, identifier, table in sorted(events, key=lambda item: (item[0], item[1], item[2])):
                if table:
                    ordered.extend((region_index, column, y, block_id) for block_id in table.block_ids)
                else:
                    ordered.append((region_index, column, y, identifier))
        # Zones outside all prose regions retain their geometric before/after position.
        for table in table_zones:
            if table_regions[table.zone_id] is None:
                center_y = (table.bbox.y0 + table.bbox.y1) / 2
                region_index = next((index for index, region in enumerate(regions)
                                     if center_y < region.bbox.y0), len(regions))
                ordered.extend((region_index, -1 if region_index < len(regions) else 0,
                                table.bbox.y0, block_id) for block_id in table.block_ids)

        ordered_ids = tuple(item[3] for item in sorted(ordered, key=lambda item: (item[0], item[1], item[2])))
        furniture_ids = set(furniture)
        ordered_ids = tuple(item for item in ordered_ids if item not in furniture_ids)
        projected = [*ordered_ids, *furniture]
        source_ids = [item[0] for item in entries]
        if len(projected) != len(source_ids) or set(projected) != set(source_ids) or len(set(source_ids)) != len(source_ids):
            _diagnostic("dev_preprocessing_layout stage=ownership_validation sourceBlockCount=%d projectedBlockCount=%d tableZoneCount=%d elapsedMs=%d failureKind=SOURCE_OWNERSHIP_MISMATCH recoveryAction=needs_review outcome=failed",
                        len(source_ids), len(projected), len(table_zones), round((time.monotonic() - started) * 1000))
            raise ValueError("SOURCE_OWNERSHIP_MISMATCH")
        plan = ReadingOrderPlan(ordered_ids, tuple(regions), tuple(profiles), tuple(spanning), tuple(furniture),
                                any(profile.ambiguous for profile in profiles),
                                tuple(finding for profile in profiles for finding in profile.findings),
                                tuple(table_zones))
        _diagnostic("dev_preprocessing_layout stage=plan_complete sourceBlockCount=%d proseBlockCount=%d tableSourceBlockCount=%d tableZoneCount=%d regionCount=%d profileCount=%d route=explicit_tables_then_prose_columns elapsedMs=%d outcome=%s",
                    len(source_ids), len(prose), sum(len(zone.block_ids) for zone in table_zones), len(table_zones),
                    len(regions), len(profiles), round((time.monotonic() - started) * 1000),
                    "review" if plan.ambiguous else "success")
        return plan

    def form_plan(self, blocks: Sequence[Any], page_geometry: PageGeometry | None = None) -> ReadingOrderPlan:
        """Preserve geometry order for forms without interpreting them as prose columns."""
        entries = [(_id(block, index), _box(block), block, _table_key(block))
                   for index, block in enumerate(blocks)]
        groups: dict[str, list[tuple[str, BoundingBox, Any, str | None]]] = defaultdict(list)
        prose = []
        for entry in entries:
            (groups[entry[3]] if entry[3] else prose).append(entry)
        zones = []
        for key, members in groups.items():
            members.sort(key=lambda item: (
                _value(item[2], "row") is None,
                _value(item[2], "row") if _value(item[2], "row") is not None else item[1].y0,
                _value(item[2], "column") is None,
                _value(item[2], "column") if _value(item[2], "column") is not None else item[1].x0,
                item[0],
            ))
            zones.append(TableZone(key.removeprefix("table:"),
                BoundingBox(min(item[1].x0 for item in members), min(item[1].y0 for item in members),
                            max(item[1].x1 for item in members), max(item[1].y1 for item in members)),
                tuple(item[0] for item in members)))
        if prose:
            bounds = page_geometry and BoundingBox(0, 0, page_geometry.width, page_geometry.height)
            if bounds is None:
                bounds = BoundingBox(min(item[1].x0 for item in prose), min(item[1].y0 for item in prose),
                                     max(item[1].x1 for item in prose), max(item[1].y1 for item in prose))
            regions = (LayoutRegion("region-1", bounds, tuple(item[0] for item in prose)),)
        else:
            regions = ()
        ordered = sorted(entries, key=lambda item: (item[1].y0, item[1].x0, item[0]))
        return ReadingOrderPlan(tuple(item[0] for item in ordered), regions, (), table_zones=tuple(zones),
                                findings=("FORM_STRUCTURE_UNSUPPORTED",))

def _diagnostic(message: str, *args: Any) -> None:
    if os.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "").lower() in {"1", "true", "yes", "on"}:
        _LOGGER.info(message, *args)
