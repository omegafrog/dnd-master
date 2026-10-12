from preprocessing_agent.layout import LayoutAnalyzer, ReadingOrderPlanner
from preprocessing_agent.domain.layout import BoundingBox, LayoutBlock, PageGeometry
from preprocessing_agent.parsers.pdf import PdfDocumentParser
from pathlib import Path
from dataclasses import replace
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


def test_line_boxes_support_two_columns_without_changing_block_ownership():
    blocks = []
    for column, left in (("left", 54), ("right", 310)):
        line_boxes = [[left, y, left + 205, y + 11] for y in range(100, 340, 12)]
        blocks.append({"block_id": column, "text": f"{column} paragraph", "bbox":
                       [left, 100, left + 205, 340], "lines": [{"bbox": box} for box in line_boxes]})

    plan = ReadingOrderPlanner().plan(blocks)

    assert plan.profiles[0].selected is not None
    assert plan.profiles[0].selected.column_count == 2
    assert plan.profiles[0].ambiguous is False
    assert set(plan.ordered_block_ids) == {"left", "right"}
    assert len(plan.ordered_block_ids) == len(set(plan.ordered_block_ids))
    assert plan.profiles[0].evidence["support_band_count"] >= 2


def test_line_band_balance_replaces_unrepresentative_block_count_balance():
    left_lines = [[54, y, 270, y + 11] for y in (100, 112, 124, 136, 160, 172, 184, 196)]
    right_lines = [[312, y + 2, 530, y + 13] for y in (100, 112, 124, 136, 160, 172, 184, 196)]
    blocks = [
        {"block_id": "left-paragraph", "text": "left", "bbox": [54, 100, 270, 207],
         "lines": [{"bbox": box} for box in left_lines]},
        {"block_id": "right-first-paragraph", "text": "right one", "bbox": [312, 102, 530, 150],
         "lines": [{"bbox": box} for box in right_lines[:4]]},
        {"block_id": "right-second-paragraph", "text": "right two", "bbox": [312, 162, 530, 209],
         "lines": [{"bbox": box} for box in right_lines[4:]]},
    ]

    profile = LayoutAnalyzer().page_plan(blocks).profiles[0]

    assert profile.selected is not None
    assert profile.selected.column_count == 2
    assert profile.ambiguous is False
    assert profile.evidence["support_band_count"] >= 2
    assert profile.confidence >= 0.8


def test_line_support_diagnostic_records_band_count_without_text(monkeypatch, caplog):
    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    caplog.set_level("INFO")
    blocks = [
        {"block_id": name, "text": "private source", "bbox": [x, 100, x + 180, 220],
         "lines": [{"bbox": [x, y, x + 180, y + 10]} for y in range(100, 220, 12)]}
        for name, x in (("left", 50), ("right", 310))
    ]

    ReadingOrderPlanner().plan(blocks)

    assert "stage=column_profile" in caplog.text
    assert "expectedSupportBands=2" in caplog.text
    assert "selectedColumns=2" in caplog.text
    assert "private source" not in caplog.text


def test_multiline_support_is_bounded_by_independent_vertical_bands():
    from preprocessing_agent.layout.analyzer import _repeated_row_support

    left = [BoundingBox(50, y, 250, y + 10) for y in range(100, 500, 12)]
    right = [BoundingBox(310, y + 2, 510, y + 12) for y in range(100, 500, 12)]

    support = _repeated_row_support(left, right, line_level=True)

    assert 2 <= support <= 6


def test_weak_single_column_evidence_remains_ambiguous():
    blocks = [
        block("main", "A long paragraph", (54, 100, 280, 180)),
        block("aside", "Short note", (330, 190, 510, 205)),
        block("tail", "Another line", (54, 220, 280, 235)),
    ]

    profile = LayoutAnalyzer().page_plan(blocks).profiles[0]

    assert profile.ambiguous or profile.selected is None or profile.selected.column_count == 1


def test_user_selected_layout_candidate_resolves_ambiguous_page_deterministically():
    blocks = [
        block("a", "A", (0, 0, 10, 20)), block("b", "B", (20, 0, 30, 20)),
        block("c", "C", (70, 0, 80, 20)), block("d", "D", (90, 0, 100, 20)),
    ]
    analyzer = LayoutAnalyzer()
    region = analyzer.analyze(blocks)[0]
    profile = analyzer.profile(region, blocks)
    two_column_index = next(index for index, candidate in enumerate(profile.candidates) if candidate.column_count == 2)

    plan = ReadingOrderPlanner().plan(blocks, selections={region.region_id: two_column_index})

    assert plan.ambiguous is False
    assert plan.profiles[0].selected is not None
    assert plan.profiles[0].selected.column_count == 2


def test_repeated_balanced_columns_beat_a_weak_footer_split():
    blocks = [
        block("toc", "2 목차", (524.464, 733.715, 577.391, 765.042)),
        block("notice", "D&D 기초 규칙", (176.133, 756.751, 426.868, 781.169)),
        block("left-1", "능력 판정", (63, 662.220, 289.001, 690.780)),
        block("left-2", "각 능력치의 사용법", (63, 674.220, 289.000, 702.780)),
        block("left-3", "내성 굴림", (63, 686.220, 288.999, 714.780)),
        block("right-1", "캐릭터 시트", (313.499, 677.058, 539.497, 705.618)),
        block("right-2", "다음엔 어떤 일이 기다릴까?", (313.499, 697.998, 539.502, 726.558)),
    ]

    profile = LayoutAnalyzer().page_plan(blocks).profiles[0]

    assert profile.ambiguous is False
    assert profile.selected is not None
    assert profile.selected.column_count == 2
    assert [(round(column.x0), round(column.x1)) for column in profile.selected.columns] == [(63, 289), (313, 577)]


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


def test_well_supported_three_columns_beat_a_two_column_merge():
    blocks = [
        block(f"{column}-{row}", f"{column}-{row}", (column * 40, row * 25, column * 40 + 25, row * 25 + 15))
        for column in range(3) for row in range(4)
    ]

    profile = ReadingOrderPlanner().plan(blocks).profiles[0]

    assert profile.ambiguous is False
    assert profile.selected is not None
    assert profile.selected.column_count == 3


def test_real_page_geometry_margins_preserve_one_two_one_regions():
    geometry = PageGeometry(612, 792)
    blocks = [
        block("title", "Title", (54, 54, 535, 76)),
        block("left", "Left", (54, 120, 280, 145)),
        block("right", "Right", (309, 120, 535, 145)),
        block("bottom", "Bottom", (54, 220, 535, 245)),
    ]
    plan = ReadingOrderPlanner().plan(blocks, geometry)
    assert [profile.selected.column_count for profile in plan.profiles if profile.selected] == [1, 2, 1]
    assert plan.ordered_block_ids == ("title", "left", "right", "bottom")
    assert set(plan.spanning_block_ids) == {"title", "bottom"}


def test_single_column_full_width_lines_are_not_spanning():
    geometry = PageGeometry(612, 792)
    blocks = [
        block("line1", "Line 1", (54, 54, 535, 76)),
        block("line2", "Line 2", (54, 120, 535, 145)),
    ]
    plan = ReadingOrderPlanner().plan(blocks, geometry)
    assert plan.spanning_block_ids == ()
    assert plan.ordered_block_ids == ("line1", "line2")


def test_layout_block_without_mapping_only_fields_is_supported():
    geometry = PageGeometry(612, 792)
    blocks = tuple(
        LayoutBlock(identifier, "text", BoundingBox(*bbox), identifier, "native", 1.0, "doc", 1, geometry)
        for identifier, bbox in (("left", (54, 120, 280, 145)), ("right", (309, 120, 535, 145)))
    )
    plan = ReadingOrderPlanner().plan(blocks, geometry)
    assert set(plan.ordered_block_ids) == {"left", "right"}


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


def test_explicit_table_cells_do_not_affect_prose_columns_and_keep_cell_order():
    blocks = [
        block("table-r2c2", "22", (60, 40, 90, 55), "table_cell") | {"table_id": "t", "row": 2, "column": 2},
        block("prose-r", "R", (60, 100, 95, 120)),
        block("table-r1c1", "11", (5, 20, 40, 35), "cell") | {"table_id": "t", "row": 1, "column": 1},
        block("prose-l", "L", (5, 100, 40, 120)),
        block("table-r1c2", "12", (60, 20, 90, 35), "table_cell") | {"table_id": "t", "row": 1, "column": 2},
        block("table-r2c1", "21", (5, 40, 40, 55), "table_cell") | {"table_id": "t", "row": 2, "column": 1},
    ]
    plan = ReadingOrderPlanner().plan(blocks)
    assert all(set(region.block_ids).isdisjoint({"table-r1c1", "table-r1c2", "table-r2c1", "table-r2c2"}) for region in plan.regions)
    assert plan.ordered_block_ids == ("table-r1c1", "table-r1c2", "table-r2c1", "table-r2c2", "prose-l", "prose-r")
    assert len(plan.profiles) == 1 and plan.profiles[0].selected.column_count == 2


def test_all_table_page_has_ordered_table_zone_and_no_prose_profiles():
    blocks = [
        block("b", "B", (30, 20, 50, 30), "table_cell") | {"table_id": "t", "row": 1, "column": 1},
        block("a", "A", (0, 20, 20, 30), "table_cell") | {"table_id": "t", "row": 0, "column": 0},
    ]
    plan = ReadingOrderPlanner().plan(blocks)
    assert plan.profiles == ()
    assert plan.ordered_block_ids == ("a", "b")
    assert plan.table_zones[0].block_ids == ("a", "b")


def test_parser_consumes_extraction_layout_plan_without_reordering_or_losing_ids(monkeypatch, caplog):
    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    caplog.set_level("INFO")
    blocks = [block("z", "Z", (60, 10, 90, 20)), block("a", "A", (0, 10, 30, 20))]
    plan = ReadingOrderPlanner().plan(blocks)
    plan = replace(plan, ordered_block_ids=tuple(reversed(plan.ordered_block_ids)))
    page = {"blocks": blocks, "layout": to_dict(plan)}
    parsed = PdfDocumentParser(lambda _: [page]).parse(Path("fixture.pdf"))
    assert [item.block_id for item in parsed.pages[0].blocks] == list(plan.ordered_block_ids)
    assert sorted(item.block_id for item in parsed.pages[0].blocks) == ["a", "z"]
    assert parsed.pages[0].layout_plan == plan
    assert "dev_preprocessing_layout stage=plan_consumed" in caplog.text
    assert "route=serialized_plan" in caplog.text and "outcome=accepted" in caplog.text


def test_table_zone_inside_right_column_is_interleaved_by_vertical_position():
    blocks = [
        block("left-1", "L1", (0, 10, 30, 20)), block("right-1", "R1", (70, 10, 100, 20)),
        block("left-2", "L2", (0, 40, 30, 50)), block("right-2", "R2", (70, 40, 100, 50)),
        block("table", "T", (70, 25, 100, 35), "table_cell") | {"table_id": "t", "row": 0, "column": 0},
        block("left-3", "L3", (0, 70, 30, 80)), block("right-3", "R3", (70, 70, 100, 80)),
    ]
    plan = ReadingOrderPlanner().plan(blocks)
    assert plan.profiles[0].selected.column_count == 2
    assert plan.ordered_block_ids == ("left-1", "left-2", "left-3", "right-1", "table", "right-2", "right-3")


def test_parser_rejects_serialized_plan_with_missing_source_owner(monkeypatch, caplog):
    from preprocessing_agent.parsers.base import ParserError
    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    caplog.set_level("INFO")
    blocks = [block("a", "A", (0, 0, 30, 20)), block("b", "B", (40, 0, 70, 20))]
    plan = ReadingOrderPlanner().plan(blocks)
    invalid = to_dict(replace(plan, ordered_block_ids=plan.ordered_block_ids[:-1]))
    import pytest
    with pytest.raises(ParserError, match="INVALID_SERIALIZED_LAYOUT_PLAN"):
        PdfDocumentParser(lambda _: [{"blocks": blocks, "layout": invalid}]).parse(Path("fixture.pdf"))
    assert "stage=plan_validation" in caplog.text
    assert "expectedBlockCount=2 actualProjectedCount=1" in caplog.text


def test_default_table_cell_group_id_matches_table_structure_detector():
    from preprocessing_agent.structure import TableStructureDetector
    blocks = [
        block("cell-a", "A", (0, 0, 20, 10), "table_cell"),
        block("cell-b", "B", (20, 0, 40, 10), "table_cell"),
    ]
    plan = ReadingOrderPlanner().plan(blocks)
    detected = TableStructureDetector().detect(blocks)
    assert len(plan.table_zones) == 1 and plan.table_zones[0].zone_id == "table-1"
    assert len(detected) == 1 and detected[0].table_id == plan.table_zones[0].zone_id


def test_plan_complete_diagnostic_reports_counts_route_and_duration(monkeypatch, caplog):
    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    caplog.set_level("INFO")
    ReadingOrderPlanner().plan([
        block("prose", "hidden text", (0, 0, 30, 20)),
        block("cell", "hidden cell", (40, 0, 60, 20), "table_cell") | {"table_id": "t"},
    ])
    assert "stage=plan_complete" in caplog.text
    assert "sourceBlockCount=2 proseBlockCount=1 tableSourceBlockCount=1 tableZoneCount=1" in caplog.text
    assert "route=explicit_tables_then_prose_columns" in caplog.text and "elapsedMs=" in caplog.text
    assert "hidden text" not in caplog.text and "hidden cell" not in caplog.text
