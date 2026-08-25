from preprocessing_agent.layout import LayoutAnalyzer, ReadingOrderPlanner


def block(block_id, text, bbox, kind="text"):
    return {"block_id": block_id, "text": text, "bbox": bbox, "kind": kind}


def test_clear_two_column_profile_and_column_major_order():
    blocks = [
        block("r2", "R2", (60, 120, 95, 140)),
        block("l2", "L2", (5, 120, 40, 140)),
        block("r1", "R1", (60, 80, 95, 100)),
        block("l1", "L1", (5, 80, 40, 100)),
    ]
    plan = ReadingOrderPlanner().plan(blocks)
    assert plan.profiles[0].selected is not None
    assert plan.profiles[0].selected.column_count == 2
    assert plan.ordered_block_ids == ("l1", "l2", "r1", "r2")
    assert len(plan.profiles[0].candidates) > 1


def test_spanning_block_is_projected_once_between_regional_columns():
    blocks = [
        block("title", "Title", (0, 0, 100, 20), "heading"),
        block("r", "R", (60, 40, 95, 60)),
        block("l", "L", (5, 40, 40, 60)),
        block("footer", "Footer", (0, 90, 100, 105), "footer"),
    ]
    plan = ReadingOrderPlanner().plan(blocks)
    assert plan.ordered_block_ids.count("title") == 1
    assert plan.ordered_block_ids.count("footer") == 0
    assert plan.furniture_block_ids == ("footer",)
    assert set(plan.ordered_block_ids) | set(plan.furniture_block_ids) == {"title", "r", "l", "footer"}


def test_regions_can_represent_one_two_one_layout():
    blocks = [
        block("top", "Top", (10, 0, 90, 20)),
        block("left", "Left", (5, 40, 40, 60)),
        block("right", "Right", (60, 40, 95, 60)),
        block("bottom", "Bottom", (10, 80, 90, 100)),
    ]
    plan = ReadingOrderPlanner().plan(blocks)
    assert [profile.selected.column_count for profile in plan.profiles if profile.selected] == [1, 2, 1]
    assert plan.ordered_block_ids == ("top", "left", "right", "bottom")


def test_ambiguous_candidate_is_retained_without_selection():
    blocks = [
        block("a", "A", (0, 0, 30, 20)),
        block("b", "B", (35, 0, 65, 20)),
        block("c", "C", (70, 0, 100, 20)),
    ]
    profile = LayoutAnalyzer().page_plan(blocks).profiles[0]
    assert profile.candidates
    assert profile.ambiguous
    assert profile.selected is None
    assert "AMBIGUOUS_COLUMN_HYPOTHESIS" in profile.findings


def test_projection_covers_each_confirmed_block_exactly_once():
    blocks = [block("a", "A", (0, 0, 40, 20)), block("b", "B", (60, 0, 100, 20)), block("c", "C", (0, 30, 100, 45))]
    plan = ReadingOrderPlanner().plan(blocks)
    assert len(plan.ordered_block_ids) == len(set(plan.ordered_block_ids))
    assert set(plan.ordered_block_ids) | set(plan.furniture_block_ids) == {"a", "b", "c"}
