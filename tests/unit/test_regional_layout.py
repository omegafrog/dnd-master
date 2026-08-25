from preprocessing_agent.layout import LayoutAnalyzer, ReadingOrderPlanner
from preprocessing_agent.parsers.pdf import PdfDocumentParser
from pathlib import Path
import json
from preprocessing_agent.domain.serialization import to_dict


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
        block("a", "A", (0, 0, 10, 20)),
        block("b", "B", (20, 0, 30, 20)),
        block("c", "C", (70, 0, 80, 20)),
        block("d", "D", (90, 0, 100, 20)),
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


def test_block_crossing_region_cut_is_assigned_once():
    blocks = [
        block("top", "Top", (0, 0, 100, 20)),
        block("cross", "Cross", (10, 15, 40, 35)),
        block("left", "Left", (5, 45, 40, 60)),
        block("right", "Right", (60, 45, 95, 60)),
    ]
    plan = ReadingOrderPlanner().plan(blocks)
    memberships = [block_id for region in plan.regions for block_id in region.block_ids]
    assert sorted(memberships) == sorted({"top", "cross", "left", "right"})
    assert len(plan.ordered_block_ids) == len(set(plan.ordered_block_ids))


def test_furniture_is_preserved_in_parsed_document_but_not_primary_order():
    parsed = PdfDocumentParser(lambda _: [{"page_number": 1, "blocks": [
        {"block_id": "header", "text": "Header", "bbox": (10, 10, 90, 20), "font_size": 8},
        {"block_id": "body", "text": "Body", "bbox": (10, 100, 90, 120), "font_size": 11},
        {"block_id": "footer", "text": "Footer", "bbox": (10, 760, 90, 770), "font_size": 8},
    ]}]).parse(Path("fixture.pdf"))
    assert {block.block_id for block in parsed.pages[0].blocks} == {"header", "body", "footer"}
    assert parsed.pages[0].blocks[0].block_id == "body"


def test_three_column_candidate_is_supported():
    blocks = [block(str(index), str(index), (left, 10, left + 25, 30)) for index, left in enumerate((0, 35, 70))]
    profile = ReadingOrderPlanner().plan(blocks).profiles[0]
    assert any(candidate.column_count == 3 for candidate in profile.candidates)


def test_layout_artifact_schema_validates_typed_plan():
    validator = __import__("pytest").importorskip("jsonschema")
    schema = json.loads(Path("schemas/layout-extraction.schema.json").read_text())
    plan = ReadingOrderPlanner().plan([block("a", "A", (0, 0, 40, 20)), block("b", "B", (60, 0, 100, 20))])
    validator.Draft202012Validator(schema).validate(to_dict(plan))


def test_ambiguous_parser_projection_is_geometry_order_and_diagnostic():
    parsed = PdfDocumentParser(lambda _: [{"blocks": [
        block("right", "Right", (70, 0, 80, 20)),
        block("left", "Left", (0, 0, 10, 20)),
        block("middle", "Middle", (35, 0, 45, 20)),
    ]}]).parse(Path("ambiguous.pdf"))
    assert [item.block_id for item in parsed.pages[0].blocks] == ["left", "middle", "right"]
    assert parsed.metadata["layout_diagnostics"][0]["layout"]["ambiguous"] is True
