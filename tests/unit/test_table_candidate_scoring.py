from preprocessing_agent.layout.table_candidate_scoring import (
    _merge_side_by_side_regions, _repeated_rectangle_count, assess_table_candidates,
)


def _aligned_grid():
    blocks = []
    lines = []
    for row, y in enumerate((20, 40, 60)):
        for column, x in enumerate((20, 100)):
            text = f"Name {row}" if column == 0 else str(10 + row)
            line = {"text": text, "bbox": (x, y, x + 40, y + 10)}
            lines.append(line)
            blocks.append({"block_id": f"r{row}c{column}", "text": text,
                           "bbox": line["bbox"], "lines": [line]})
    return blocks, lines


def test_repeated_rows_and_columns_cross_high_confidence_threshold():
    blocks, lines = _aligned_grid()
    result = assess_table_candidates(blocks=blocks, lines=lines, drawings=[], image_regions=[],
                                     native_candidates=[], width=200, height=100, page_number=1)
    assert result["components"]["column_alignment"] == 35
    assert result["components"]["row_structure"] == 25
    assert result["score"] >= 55
    assert result["route"] == "extract"


def test_grid_and_repeated_fill_signals_complete_the_configured_score_weights():
    blocks, lines = _aligned_grid()
    items = []
    for y in (15, 35, 55, 75):
        items.append(("l", (15, y), (185, y)))
    for x in (15, 95, 185):
        items.append(("l", (x, 15), (x, 75)))
    for y in (15, 35, 55):
        items.append(("re", (15, y, 185, y + 20)))
    result = assess_table_candidates(blocks=blocks, lines=lines,
                                     drawings=[{"items": items, "fill": (0.8, 0.8, 0.8)}],
                                     image_regions=[], native_candidates=[], width=200, height=100,
                                     page_number=1)
    assert result["components"] == {
        "column_alignment": 35, "row_structure": 25, "grid_borders": 20,
        "background_color": 15, "table_pattern": 5,
    }
    assert result["score"] == 100


def test_ordinary_single_column_prose_skips_image_processing_except_audit_sample():
    lines = [{"text": f"A prose sentence for page {page} ends here.",
              "bbox": (20, 20 + page * 12, 180, 30 + page * 12)} for page in range(4)]
    blocks = [{"block_id": str(index), "text": line["text"], "bbox": line["bbox"], "lines": [line]}
              for index, line in enumerate(lines)]
    result = assess_table_candidates(blocks=blocks, lines=lines, drawings=[], image_regions=[],
                                     native_candidates=[], width=220, height=100, page_number=1)
    assert result["score"] < 20
    assert result["route"] == "skip"


def test_two_column_prose_is_penalized_and_never_directly_extracted():
    lines = []
    blocks = []
    for row, y in enumerate((20, 40, 60, 80)):
        for column, x in enumerate((15, 125)):
            text = f"This is a long paragraph line in column {column} row {row} continues" \
                   " with more ordinary prose rather than a cell."
            line = {"text": text, "bbox": (x, y, x + 95, y + 10)}
            lines.append(line)
            blocks.append({"block_id": f"r{row}c{column}", "text": text,
                           "bbox": line["bbox"], "lines": [line]})
    result = assess_table_candidates(blocks=blocks, lines=lines, drawings=[], image_regions=[],
                                     native_candidates=[], width=230, height=110, page_number=3)
    assert any(item["reason"] == "INDEPENDENT_PROSE_COLUMNS" for item in result["penalties"])
    assert result["score"] < 55
    assert result["route"] == "screen"


def test_two_column_prose_with_shorter_pdf_lines_is_screened_not_directly_extracted():
    lines = []
    blocks = []
    for row, y in enumerate((15, 30, 45, 60, 75, 90)):
        for column, x in enumerate((15, 125)):
            text = (f"This paragraph explains the rule clearly {row}." if column == 0
                    else f"Another paragraph describes the effect clearly {row}.")
            line = {"text": text, "bbox": (x, y, x + 95, y + 8)}
            lines.append(line)
            blocks.append({"block_id": f"r{row}c{column}", "text": text,
                           "bbox": line["bbox"], "lines": [line]})

    result = assess_table_candidates(blocks=blocks, lines=lines, drawings=[], image_regions=[],
                                     native_candidates=[], width=240, height=110, page_number=2)

    assert any(item["reason"] == "INDEPENDENT_PROSE_COLUMNS" for item in result["penalties"])
    assert result["route"] == "screen"


def test_background_signal_requires_rectangles_to_align_as_rows_or_columns():
    scattered = [(10, 10, 30, 20), (70, 35, 90, 45), (140, 70, 160, 80)]
    row_cells = [(10, 10, 50, 20), (60, 10, 100, 20), (110, 10, 150, 20)]

    assert _repeated_rectangle_count(scattered) == 0
    assert _repeated_rectangle_count(row_cells) == 3


def test_adjacent_regions_covering_the_same_rows_use_one_extraction_crop():
    regions = [(309, 61, 418, 98), (431, 61, 540, 98), (54, 200, 280, 300)]

    assert _merge_side_by_side_regions(regions) == [(309, 61, 540, 98), (54, 200, 280, 300)]


def test_near_page_native_box_does_not_expand_a_local_high_score_extraction():
    blocks, lines = _aligned_grid()
    native = type("Candidate", (), {"bbox": (0, 0, 199, 99)})()

    result = assess_table_candidates(blocks=blocks, lines=lines, drawings=[], image_regions=[],
                                     native_candidates=[(native, "text")], width=200, height=100,
                                     page_number=3)

    assert result["route"] == "extract"
    assert len(result["extraction_regions"]) == 1
    assert result["extraction_regions"][0] == [0.0, 20.0, 200, 70.0]


def test_explicit_native_candidate_and_low_score_audit_are_promoted_to_screening():
    candidate = type("Candidate", (), {"bbox": (10, 10, 90, 40)})()
    lines = [{"text": "Short line.", "bbox": (10, 10, 50, 20)}]
    blocks = [{"block_id": "body", "text": "Short line.", "bbox": lines[0]["bbox"], "lines": lines}]
    with_native = assess_table_candidates(blocks=blocks, lines=lines, drawings=[], image_regions=[],
                                          native_candidates=[(candidate, "lines")],
                                          width=100, height=100, page_number=1)
    assert with_native["route"] == "screen"
    assert "NATIVE_TABLE_DETECTION" in with_native["exceptions"]
    samples = [assess_table_candidates(
        blocks=blocks, lines=[{**lines[0], "text": f"Short line {page}."}], drawings=[], image_regions=[],
        native_candidates=[], width=100, height=100, page_number=page,
    ) for page in range(1, 101)]
    sample_count = sum(item["audit_sample"] for item in samples)
    assert 5 <= sample_count <= 15
    assert all(item["route"] == "screen" for item in samples if item["audit_sample"])
    assert all(item["region_bbox"] == [0.0, 0.0, 100.0, 100.0]
               for item in samples if item["audit_sample"])
    assert all([0.0, 0.0, 100.0, 100.0] not in item["extraction_regions"]
               for item in samples if item["audit_sample"])
