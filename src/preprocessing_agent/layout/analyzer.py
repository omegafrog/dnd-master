"""Conservative, vendor-neutral regional column analysis."""
from __future__ import annotations

from collections.abc import Mapping, Sequence
from typing import Any

from preprocessing_agent.domain.layout import (
    BoundingBox, ColumnHypothesis, ColumnProfile, LayoutRegion, ReadingOrderPlan,
)


def _box(block: Any) -> BoundingBox:
    value = block.bbox if hasattr(block, "bbox") else block.get("bbox")
    if value is None:
        raise ValueError("layout analysis requires block geometry")
    return value if isinstance(value, BoundingBox) else BoundingBox(*(float(x) for x in value))


def _id(block: Any, index: int) -> str:
    value = block.block_id if hasattr(block, "block_id") else block.get("block_id")
    return str(value or f"block-{index}")


class LayoutAnalyzer:
    """Splits a page at spanning geometry, then scores regional partitions."""

    def analyze(self, blocks: Sequence[Any], page_geometry: Any | None = None) -> tuple[LayoutRegion, ...]:
        if not blocks:
            return ()
        entries = [(_id(block, i), _box(block), block) for i, block in enumerate(blocks)]
        left = min(item[1].x0 for item in entries)
        right = max(item[1].x1 for item in entries)
        width = (page_geometry.width if page_geometry is not None else right - left)
        page_left = 0.0 if page_geometry is not None else left
        page_right = width if page_geometry is not None else right
        # Blocks crossing most of the page are separators/spanning content. They
        # delimit regions but remain part of the final projection.
        spanning = [item for item in entries if item[1].x0 <= page_left + width * .08 and item[1].x1 >= page_right - width * .08]
        cuts = sorted({y for _, box, _ in spanning for y in (box.y0, box.y1)})
        y_min = min(box.y0 for _, box, _ in entries)
        y_max = max(box.y1 for _, box, _ in entries)
        boundaries = [y_min] + cuts + [y_max]
        regions: list[LayoutRegion] = []
        for start, end in zip(boundaries, boundaries[1:]):
            # Assign by block centre so a block crossing a cut belongs to one
            # region only. Boundary ties are resolved toward the later region.
            members = [item for item in entries if start <= (item[1].y0 + item[1].y1) / 2 <= end]
            if not members:
                continue
            # A spanning block belongs to the nearest region; it is separately
            # identified by the planner for insertion at its geometric position.
            ids = tuple(item[0] for item in members)
            regions.append(LayoutRegion(f"region-{len(regions)+1}", BoundingBox(page_left, start, page_right, end), ids))
        if not regions:
            regions.append(LayoutRegion("region-1", BoundingBox(page_left, y_min, page_right, y_max), tuple(item[0] for item in entries)))
        return tuple(regions)

    def profile(self, region: LayoutRegion, blocks: Sequence[Any], *, ambiguity_margin: float = .08) -> ColumnProfile:
        by_id = {_id(block, i): block for i, block in enumerate(blocks)}
        members = [by_id[block_id] for block_id in region.block_ids if block_id in by_id]
        entries = [(_box(block), block) for block in members]
        if not entries:
            raise ValueError("region has no blocks")
        candidates: list[ColumnHypothesis] = []
        overall_left = min(box.x0 for box, _ in entries)
        overall_right = max(box.x1 for box, _ in entries)
        overall_top = min(box.y0 for box, _ in entries)
        overall_bottom = max(box.y1 for box, _ in entries)
        whole = BoundingBox(overall_left, overall_top, overall_right, overall_bottom)
        candidates.append(ColumnHypothesis(1, (whole,), .55, "single-column"))
        starts = sorted({box.x0 for box, _ in entries})
        gaps = [(b - a, a, b) for a, b in zip(starts, starts[1:]) if b > a]
        # Evaluate every meaningful gap, not just the largest page-wide gap.
        for gap, a, b in gaps:
            left_items = [box for box, _ in entries if box.x1 <= b]
            right_items = [box for box, _ in entries if box.x0 >= b]
            if len(left_items) < 1 or len(right_items) < 1:
                continue
            overlapping_rows = sum(
                1 for left_box in left_items for right_box in right_items
                if min(left_box.y1, right_box.y1) > max(left_box.y0, right_box.y0)
            )
            if not overlapping_rows:
                # An isolated header/sidebar must not turn a single-column
                # body into a page-wide two-column hypothesis.
                continue
            separation = gap / max(overall_right - overall_left, 1.0)
            balance = min(len(left_items), len(right_items)) / max(len(entries), 1)
            score = min(1.0, .45 + separation + balance * .35)
            candidates.append(ColumnHypothesis(2, (BoundingBox(overall_left, overall_top, max(box.x1 for box in left_items), overall_bottom), BoundingBox(min(box.x0 for box in right_items), overall_top, overall_right, overall_bottom)), score, "gutter"))
        # N-column candidates are formed from the strongest x gutters. This is
        # intentionally geometry-only; semantic table interpretation belongs
        # to the following plan.
        for count in range(3, min(4, len(starts)) + 1):
            chosen = sorted(gaps, reverse=True)[: count - 1]
            cuts = []
            for _, _, right_start in chosen:
                left_boxes = [box for box, _ in entries if box.x0 < right_start]
                right_boxes = [box for box, _ in entries if box.x0 >= right_start]
                if not left_boxes or not right_boxes:
                    cuts = []
                    break
                cuts.append((max(box.x1 for box in left_boxes) + min(box.x0 for box in right_boxes)) / 2)
            cuts.sort()
            if len(cuts) != count - 1:
                continue
            bands = [overall_left, *cuts, overall_right]
            groups = [[box for box, _ in entries if bands[index] <= box.x0 and box.x1 <= bands[index + 1]] for index in range(count)]
            if any(not group for group in groups):
                continue
            if any(not any(min(left_box.y1, right_box.y1) > max(left_box.y0, right_box.y0) for left_box in groups[index] for right_box in groups[index + 1]) for index in range(count - 1)):
                continue
            balance = min(len(group) for group in groups) / max(len(entries), 1)
            separation = sum(item[0] for item in chosen) / max(overall_right - overall_left, 1.0)
            score = min(1.0, .45 + separation + balance * .35)
            candidates.append(ColumnHypothesis(count, tuple(BoundingBox(min(box.x0 for box in group), overall_top, max(box.x1 for box in group), overall_bottom) for group in groups), score, "gutter-cluster"))
        candidates.sort(key=lambda item: (-item.score, item.column_count))
        best = candidates[0]
        second = candidates[1] if len(candidates) > 1 else None
        ambiguous = second is not None and best.score - second.score < ambiguity_margin
        return ColumnProfile(region.region_id, tuple(candidates), None if ambiguous else best, best.score if not ambiguous else best.score - (ambiguity_margin / 2), ambiguous, ("AMBIGUOUS_COLUMN_HYPOTHESIS",) if ambiguous else ())

    def page_plan(self, blocks: Sequence[Any], page_geometry: Any | None = None) -> ReadingOrderPlan:
        regions = self.analyze(blocks, page_geometry)
        profiles = tuple(self.profile(region, blocks) for region in regions)
        return ReadingOrderPlan((), regions, profiles, ambiguous=any(profile.ambiguous for profile in profiles), findings=tuple(finding for profile in profiles for finding in profile.findings))
