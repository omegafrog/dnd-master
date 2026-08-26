"""Reading-order projection for regional layout evidence."""
from __future__ import annotations

from collections.abc import Sequence
from typing import Any

from preprocessing_agent.domain.layout import BoundingBox, ReadingOrderPlan
from .analyzer import LayoutAnalyzer, _box, _id


class ReadingOrderPlanner:
    def __init__(self, analyzer: LayoutAnalyzer | None = None) -> None:
        self.analyzer = analyzer or LayoutAnalyzer()

    def plan(self, blocks: Sequence[Any], page_geometry: Any | None = None) -> ReadingOrderPlan:
        if not blocks:
            return ReadingOrderPlan((), (), ())  # type: ignore[arg-type]
        regions = self.analyzer.analyze(blocks, page_geometry)
        by_id = {_id(block, i): block for i, block in enumerate(blocks)}
        profiles = tuple(self.analyzer.profile(region, blocks) for region in regions)
        multi_region = any(profile.selected is not None and profile.selected.column_count > 1 for profile in profiles)
        all_boxes = [_box(block) for block in blocks]
        content_left = min(box.x0 for box in all_boxes)
        content_right = max(box.x1 for box in all_boxes)
        content_width = max(content_right - content_left, 1.0)
        ordered: list[tuple[int, int, float, str]] = []
        spanning: list[str] = []
        furniture: list[str] = []
        for region_index, (region, profile) in enumerate(zip(regions, profiles)):
            members = [(_id(block, i), block) for i, block in enumerate(blocks) if _id(block, i) in region.block_ids]
            if profile.selected is None:
                # Ambiguous geometry is never silently accepted. A stable y/x
                # projection is retained solely as diagnostic ordering.
                members.sort(key=lambda item: (_box(item[1]).y0, _box(item[1]).x0, item[0]))
                ordered.extend((region_index, 0, _box(block).y0, block_id) for block_id, block in members)
                continue
            columns = profile.selected.columns
            region_spanning = []
            for block_id, block in members:
                box = _box(block)
                font_size = getattr(block, "font_size", None)
                if font_size is None and hasattr(block, "get"):
                    font_size = block.get("font_size")
                edge_furniture = font_size is not None and float(font_size) <= 9 and (box.y0 < 60 or box.y1 > 720)
                kind = getattr(block, "kind", "")
                if not kind and hasattr(block, "get"):
                    kind = block.get("kind", "")
                named_furniture = str(kind).lower() in {"header", "footer", "repeated-header", "repeated-footer"}
                broad_block = (
                    multi_region
                    and box.x0 <= content_left + content_width * .08
                    and box.x1 >= content_right - content_width * .08
                    and (box.x1 - box.x0) / content_width >= .82
                )
                if broad_block:
                    # A title/footer can be both spanning and furniture. Keep
                    # both roles in evidence while excluding furniture from
                    # the primary order below.
                    region_spanning.append((block_id, block))
                    if edge_furniture or named_furniture:
                        furniture.append(block_id)
                elif edge_furniture or named_furniture:
                    furniture.append(block_id)
                elif len(columns) > 1 and box.x0 <= columns[0].x0 and box.x1 >= columns[-1].x1:
                    region_spanning.append((block_id, block))
                elif str(getattr(block, "kind", "") if hasattr(block, "kind") else block.get("kind", "")).lower() in {"header", "footer", "repeated-header", "repeated-footer"}:
                    furniture.append(block_id)
                else:
                    column = next((index for index, column_box in enumerate(columns) if box.x0 >= column_box.x0 and box.x1 <= column_box.x1), 0)
                    # Attach an ephemeral sorting key; the output remains a
                    # block-id sequence and never mutates source blocks.
                    ordered.append((region_index, column, _box(block).y0, block_id))
            for block_id, block in region_spanning:
                spanning.append(block_id)
                ordered.append((region_index, -1, _box(block).y0, block_id))
        ordered_ids = tuple(item[3] for item in sorted(ordered, key=lambda item: (item[0], item[1], item[2], item[3])))
        # Furniture is preserved in evidence but excluded from primary order.
        ordered_ids = tuple(item for item in ordered_ids if item not in set(furniture))
        return ReadingOrderPlan(ordered_ids, regions, profiles, tuple(spanning), tuple(furniture), any(profile.ambiguous for profile in profiles), tuple(finding for profile in profiles for finding in profile.findings))
