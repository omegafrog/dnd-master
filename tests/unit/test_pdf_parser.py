from pathlib import Path
from types import SimpleNamespace

from preprocessing_agent.parsers.pdf import PdfDocumentParser, _classify_text_detection_hypothesis, _find_native_tables, _is_reliable_native_table, _native_table_candidate_diagnostic, _reconcile_native_table_candidates, _reconstruct_native_table, _without_consumed_header_blocks
from preprocessing_agent.structure import TableStructureDetector


def test_parser_excludes_only_geometry_confirmed_forms_and_records_original_page_numbers(tmp_path):
    pages = [
        {"page_number": 1, "page_classification": "form", "form_diagnostics": {
            "drawing_count": 180, "aligned_line_count": 466,
            "long_horizontal_line_count": 154, "text_line_count": 15},
         "blocks": [{"block_id": "field", "text": "Strength", "bbox": (10, 10, 40, 20)}]},
        {"page_number": 2, "blocks": [{"block_id": "rule", "text": "Rule text", "bbox": (10, 10, 90, 20)}]},
    ]

    document = PdfDocumentParser(extractor=lambda _source: pages).parse(tmp_path / "source.pdf")

    assert [page.page_number for page in document.pages] == [2]
    assert document.source_text == "Rule text"
    assert document.metadata["source_page_count"] == 2
    assert document.metadata["excluded_form_pages"] == (1,)
    assert document.metadata["excluded_form_evidence"][0]["form_diagnostics"]["drawing_count"] == 180


def test_pymupdf_native_table_cells_are_projected_when_supported(tmp_path):
    fitz = __import__("pytest").importorskip("fitz")
    document = fitz.open()
    page = document.new_page(width=240, height=160)
    for x in (20, 120, 220):
        page.draw_line((x, 30), (x, 100), color=(0, 0, 0))
    for y in (30, 60, 100):
        page.draw_line((20, y), (220, y), color=(0, 0, 0))
    page.insert_text((35, 50), "Name")
    page.insert_text((135, 50), "Value")
    page.insert_text((35, 85), "Dex")
    page.insert_text((135, 85), "12")
    source = tmp_path / "table.pdf"
    document.save(source)
    document.close()
    if not hasattr(page, "find_tables"):
        __import__("pytest").skip("PyMuPDF table finder is unavailable")
    parsed = PdfDocumentParser().parse(source)
    tables = parsed.pages[0].tables
    assert tables, "native table finder must expose a structured table"
    assert tables[0].table_id
    assert {cell.text for cell in tables[0].cells} >= {"Name", "Value", "Dex", "12"}


def test_weak_native_table_detection_is_rejected_before_cell_projection():
    weak_one_row = SimpleNamespace(rows=(SimpleNamespace(cells=((0, 0, 20, 10), (20, 0, 40, 10))),))
    weak_missing_cell = SimpleNamespace(rows=(
        SimpleNamespace(cells=((0, 0, 20, 10), (20, 0, 40, 10))),
        SimpleNamespace(cells=((0, 10, 20, 20), None)),
    ))

    assert _is_reliable_native_table(weak_one_row) is False
    assert _is_reliable_native_table(weak_missing_cell) is True
    weak_missing_cell.bbox = (0, 0, 40, 20)
    _, missing_diagnostic = _reconstruct_native_table(weak_missing_cell, [])
    assert missing_diagnostic["decision"] == "strong_unresolved"


def test_partial_native_grid_geometry_reaches_reconstruction_but_stays_unresolved():
    table = SimpleNamespace(bbox=(0, 0, 100, 30), rows=(
        SimpleNamespace(cells=((0, 0, 50, 10), (50, 0, 100, 10))),
        SimpleNamespace(cells=((0, 10, 50, 20), None)),
        SimpleNamespace(cells=((0, 20, 50, 30), (50, 20, 100, 30))),
    ), extract=lambda: [["A", "B"], ["C", None], ["D", "E"]])
    projected, diagnostic = _reconstruct_native_table(table, [])
    assert projected == []
    assert diagnostic["decision"] == "strong_unresolved"
    assert diagnostic["rejection_reason"] == "GRID_RECONSTRUCTION_AMBIGUOUS"


def test_native_detection_compares_strategies_before_candidate_reconstruction(monkeypatch, caplog):
    def table(boxes):
        rows = tuple(SimpleNamespace(cells=(box, (box[0] + 50, box[1], box[2] + 50, box[3])))
                     for box in boxes)
        return SimpleNamespace(bbox=(0, 0, 100, 40), rows=rows)

    default_candidate = table(((0, 0, 50, 20), (0, 20, 50, 40)))
    text_candidate = table(((0, 0, 50, 20), (0, 20, 50, 40), (0, 40, 50, 60)))
    class Page:
        def find_tables(self, **kwargs):
            return SimpleNamespace(tables=(text_candidate,) if kwargs else (default_candidate,))

    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    caplog.set_level("INFO")
    candidates = _find_native_tables(Page(), 7)
    assert candidates == [(default_candidate, "lines"), (text_candidate, "text")]
    reconstructed = []
    for candidate, strategy in candidates:
        cells, diagnostic = _reconstruct_native_table(candidate, [])
        diagnostic.update(detection_strategies=[strategy], detection_deduplicated=False)
        _classify_text_detection_hypothesis(diagnostic, strategy)
        reconstructed.append((cells, diagnostic))
    selected, diagnostics = _reconcile_native_table_candidates(candidates, reconstructed, 7)
    assert len(selected) == 1
    assert selected[0][1] == "lines+text"
    assert selected[0][3]["decision"] == "strong_unresolved"
    assert len(diagnostics) == 2
    assert diagnostics[1]["decision"] == "weak_candidate"
    logs = " ".join(record.getMessage() for record in caplog.records)
    assert "strategy=lines" in logs and "strategy=text" in logs
    assert "overlap_deduplicated" in logs and "keep_best_reconstruction" in logs


def test_contained_text_candidate_does_not_replace_precise_line_candidate():
    def table(bbox, rows):
        return SimpleNamespace(bbox=bbox, rows=tuple(SimpleNamespace(cells=cells) for cells in rows),
                               extract=lambda: [["A", "B"], ["C", "D"]])

    precise = table((10, 40, 90, 60), (((10, 40, 50, 50), (50, 40, 90, 50)),
                                       ((10, 50, 50, 60), (50, 50, 90, 60))))
    broad = table((0, 0, 100, 100), (((0, 0, 50, 50), (50, 0, 100, 50)),
                                     ((0, 50, 50, 100), (50, 50, 100, 100))))
    class Page:
        def find_tables(self, **kwargs):
            return SimpleNamespace(tables=(broad,) if kwargs else (precise,))

    candidates = _find_native_tables(Page(), 23)
    assert candidates == [(precise, "lines"), (broad, "text")]


def test_unresolved_text_candidate_remains_diagnostic_without_becoming_confirmed():
    candidate = SimpleNamespace(bbox=(0, 0, 100, 100), rows=(
        SimpleNamespace(cells=((0, 0, 50, 50), (50, 0, 100, 50))),
        SimpleNamespace(cells=((0, 50, 50, 100), (50, 50, 100, 100))),
    ), extract=lambda: [["A", "B"], ["C", "D"]])
    _, diagnostic = _reconstruct_native_table(candidate, [])
    assert diagnostic["decision"] == "strong_unresolved"
    _classify_text_detection_hypothesis(diagnostic, "text")
    assert diagnostic["decision"] == "weak_candidate"
    assert diagnostic["candidate_kind"] == "unresolved_text_detection"
    assert diagnostic["reconstruction_decision"] == "strong_unresolved"
    assert diagnostic["reconstruction_rejection_reason"] == "TABLE_TEXT_MISMATCH"
    assert "TEXT_DETECTION_RECONSTRUCTION_UNRESOLVED" in diagnostic["signals"]

    unresolved = {"decision": "strong_unresolved", "rejection_reason": "GRID_RECONSTRUCTION_AMBIGUOUS",
                  "signals": []}
    _classify_text_detection_hypothesis(unresolved, "lines")
    assert unresolved["decision"] == "strong_unresolved"


def test_reported_partial_native_grid_shapes_are_strong_but_not_reconstructed():
    def candidate(rows, columns, missing):
        cells = []
        index = 0
        for row in range(rows):
            row_cells = []
            for column in range(columns):
                if index < missing:
                    row_cells.append(None)
                else:
                    x0, y0 = 10 + column * 5, 10 + row * 3
                    row_cells.append((x0, y0, x0 + 4, y0 + 2))
                index += 1
            cells.append(SimpleNamespace(cells=tuple(row_cells)))
        return SimpleNamespace(bbox=(10, 10, 200, 300), rows=tuple(cells))

    for rows, columns, missing in ((19, 13, 108), (5, 5, 8), (41, 7, 132), (33, 2, 16), (27, 2, 13)):
        diagnostic = _native_table_candidate_diagnostic(candidate(rows, columns, missing))
        assert diagnostic["decision"] == "strong_unresolved"
        assert diagnostic["row_count"] == rows and diagnostic["column_count"] == columns
        assert diagnostic["missing_cell_count"] == missing
        assert diagnostic["bbox"] == [10, 10, 200, 300]
        assert diagnostic["rejection_reason"] == "MISSING_CELL_BBOXES"
        assert any(signal.startswith("DISTINCT_ROW_CENTER_COUNT_") and int(signal.rsplit("_", 1)[1]) >= 3
                   for signal in diagnostic["signals"])
        assert any(signal.startswith("X_EDGE_SUPPORT_ACROSS_ROWS_COUNT_")
                   and int(signal.rsplit("_", 1)[1]) >= min(columns + 1, 3)
                   for signal in diagnostic["signals"])


def test_native_grid_reconstructs_merged_full_row_and_standard_row():
    table = SimpleNamespace(bbox=(0, 0, 100, 40), rows=(
        SimpleNamespace(cells=((0, 0, 100, 20), None)),
        SimpleNamespace(cells=((0, 20, 50, 40), (50, 20, 100, 40))),
    ), extract=lambda: [["Title", None], ["A", "B"]])
    projected, diagnostic = _reconstruct_native_table(table, [
        {"text": "Title", "bbox": (10, 5, 40, 15)},
        {"text": "A", "bbox": (10, 25, 20, 35)},
        {"text": "B", "bbox": (60, 25, 70, 35)},
    ])
    assert diagnostic["decision"] == "confirmed_structured"
    assert [(cell["row"], cell["column"], cell["column_span"], cell["merged"]) for cell in projected] == [
        (0, 0, 2, True), (1, 0, 1, False), (1, 1, 1, False),
    ]


def test_native_grid_text_mismatch_stays_unresolved():
    table = SimpleNamespace(bbox=(0, 0, 100, 40), rows=(
        SimpleNamespace(cells=((0, 0, 50, 20), (50, 0, 100, 20))),
        SimpleNamespace(cells=((0, 20, 50, 40), (50, 20, 100, 40))),
    ), extract=lambda: [["A", "B"], ["C", "D"]])
    projected, diagnostic = _reconstruct_native_table(table, [
        {"text": "A", "bbox": (2, 2, 10, 10)}, {"text": "B", "bbox": (52, 2, 60, 10)},
        {"text": "C", "bbox": (2, 22, 10, 30)}, {"text": "wrong", "bbox": (52, 22, 70, 30)},
    ])
    assert projected == []
    assert diagnostic["decision"] == "strong_unresolved"
    assert diagnostic["rejection_reason"] == "TABLE_TEXT_MISMATCH"


def test_native_wide_box_splits_only_when_source_spans_map_to_columns():
    table = SimpleNamespace(bbox=(0, 0, 100, 40), rows=(
        SimpleNamespace(cells=((0, 0, 100, 20), None)),
        SimpleNamespace(cells=((0, 20, 50, 40), (50, 20, 100, 40))),
    ), extract=lambda: [["Name Value", None], ["A", "B"]])
    source = [
        {"text": "Name Value", "bbox": (0, 0, 100, 20), "spans": [
            {"text": "Name", "bbox": (10, 5, 25, 15)},
            {"text": "Value", "bbox": (60, 5, 80, 15)},
        ]},
        {"text": "A", "bbox": (0, 20, 50, 40), "spans": [{"text": "A", "bbox": (10, 25, 20, 35)}]},
        {"text": "B", "bbox": (50, 20, 100, 40), "spans": [{"text": "B", "bbox": (60, 25, 70, 35)}]},
    ]
    projected, diagnostic = _reconstruct_native_table(table, source)
    assert diagnostic["decision"] == "confirmed_structured"
    assert [(cell["row"], cell["column"], cell["text"]) for cell in projected] == [
        (0, 0, "Name"), (0, 1, "Value"), (1, 0, "A"), (1, 1, "B"),
    ]


def test_native_header_spans_map_to_columns_and_shift_data_rows():
    table = SimpleNamespace(bbox=(0, 20, 200, 60), rows=(
        SimpleNamespace(cells=tuple((column * 50, 20, column * 50 + 50, 40) for column in range(4))),
        SimpleNamespace(cells=tuple((column * 50, 40, column * 50 + 50, 60) for column in range(4))),
    ), extract=lambda: [["1", "2", "3", "4"], ["5", "6", "7", "8"]])
    labels = ("Level", "Proficiency Bonus", "Features", "Cantrips Known")
    label_lines = ((0, "Level", 5, 15), (1, "Proficiency", 3, 9), (1, "Bonus", 11, 17),
                   (2, "Features", 5, 15), (3, "Cantrips", 3, 9), (3, "Known", 11, 17))
    source = [{"text": label, "bbox": (column * 50 + 5, y0, column * 50 + 40, y1),
               "spans": [{"text": label, "bbox": (column * 50 + 5, y0, column * 50 + 40, y1)}]}
              for column, label, y0, y1 in label_lines]
    source += [{"text": value, "bbox": (column * 50 + 5, row * 20 + 5, column * 50 + 15, row * 20 + 15)}
               for row, values in enumerate((("1", "2", "3", "4"), ("5", "6", "7", "8")), start=1)
               for column, value in enumerate(values)]
    projected, diagnostic = _reconstruct_native_table(table, source)
    assert diagnostic["decision"] == "confirmed_structured"
    headers = [cell for cell in projected if cell["is_header"]]
    assert [(cell["column"], cell["text"]) for cell in headers] == list(enumerate(labels))
    assert all(cell["row"] == 0 for cell in headers)
    assert {cell["row"] for cell in projected if not cell["is_header"]} == {1, 2}
    structures = TableStructureDetector().detect(
        [{**cell, "table_id": "p31-table-0", "block_id": f"c{index}"}
         for index, cell in enumerate(projected)])
    assert [cell.text for cell in structures[0].header_rows[0].cells] == list(labels)
    remaining = _without_consumed_header_blocks(source, projected)
    assert len(remaining) == 8
    assert all(block["text"] not in labels for block in remaining)


def test_native_table_geometry_scales_with_text_and_row_heights():
    for scale in (0.5, 2.0):
        table = SimpleNamespace(bbox=(0, 20 * scale, 100 * scale, 60 * scale), rows=(
            SimpleNamespace(cells=((0, 20 * scale, 50 * scale, 40 * scale),
                                   (50 * scale, 20 * scale, 100 * scale, 40 * scale))),
            SimpleNamespace(cells=((0, 40 * scale, 50 * scale, 60 * scale),
                                   (50 * scale, 40 * scale, 100 * scale, 60 * scale))),
        ), extract=lambda: [["A", "B"], ["C", "D"]])
        source = []
        for column, text, y0, y1 in ((0, "Left", 5, 9), (1, "Right", 5, 9)):
            box = (column * 50 * scale + 5 * scale, y0 * scale,
                   column * 50 * scale + 20 * scale, y1 * scale)
            source.append({"text": text, "bbox": box, "spans": [{"text": text, "bbox": box}]})
        source.extend({"text": text, "bbox": (column * 50 * scale + 5 * scale, y * scale,
                                                  column * 50 * scale + 15 * scale, (y + 4) * scale)}
                      for y, row in ((22, ("A", "B")), (42, ("C", "D")))
                      for column, text in enumerate(row))
        projected, diagnostic = _reconstruct_native_table(table, source)
        assert diagnostic["decision"] == "confirmed_structured"
        assert diagnostic["mapping_tolerance"] > 0
        assert [(cell["row"], cell["column"], cell["text"]) for cell in projected] == [
            (0, 0, "Left"), (0, 1, "Right"), (1, 0, "A"), (1, 1, "B"),
            (2, 0, "C"), (2, 1, "D"),
        ]


def test_p57_style_neighboring_tables_stay_independent_without_header_evidence():
    tables = [SimpleNamespace(bbox=(x, 20, x + 100, 60), rows=(
        SimpleNamespace(cells=((x, 20, x + 50, 40), (x + 50, 20, x + 100, 40))),
        SimpleNamespace(cells=((x, 40, x + 50, 60), (x + 50, 40, x + 100, 60))),
    ), extract=lambda: [["A", "B"], ["C", "D"]]) for x in (0, 150)]
    structures = []
    for index, table in enumerate(tables):
        x = table.bbox[0]
        source = [{"text": value, "bbox": (left + 5, top + 5, left + 15, top + 15)}
                  for top, values in ((20, ("A", "B")), (40, ("C", "D")))
                  for left, value in zip((x, x + 50), values)]
        projected, diagnostic = _reconstruct_native_table(table, source)
        assert diagnostic["decision"] == "confirmed_structured"
        assert not any(cell["is_header"] for cell in projected)
        structures.extend({**cell, "table_id": f"p57-table-{index}", "block_id": f"t{index}-{cell['row']}-{cell['column']}"}
                          for cell in projected)
    detected = TableStructureDetector().detect(structures)
    assert [table.table_id for table in detected] == ["p57-table-0", "p57-table-1"]
    assert all(not table.header_rows and len(table.rows) == 2 for table in detected)


def test_external_header_keeps_unlabeled_inferred_columns_structural():
    table = SimpleNamespace(bbox=(0, 20, 150, 60), rows=(
        SimpleNamespace(cells=((0, 20, 50, 40), (50, 20, 100, 40), (100, 20, 150, 40))),
        SimpleNamespace(cells=((0, 40, 50, 60), (50, 40, 100, 60), (100, 40, 150, 60))),
    ), extract=lambda: [["A", "B", "C"], ["D", "E", "F"]])
    source = [{"text": value, "bbox": (x, 5, x + 15, 15),
               "spans": [{"text": value, "bbox": (x, 5, x + 15, 15)}]}
              for x, value in ((5, "Name"), (55, "Cost"))]
    source += [{"text": value, "bbox": (x + 5, y + 5, x + 15, y + 15)}
               for y, values in ((20, ("A", "B", "C")), (40, ("D", "E", "F")))
               for x, value in zip((0, 50, 100), values)]
    projected, diagnostic = _reconstruct_native_table(table, source)
    assert diagnostic["decision"] == "confirmed_structured"
    structures = TableStructureDetector().detect(
        [{**cell, "table_id": "p48-table-0", "block_id": f"c{index}"}
         for index, cell in enumerate(projected)])
    assert len(structures[0].header_rows[0].cells) == 3
    assert not structures[0].header_rows[0].cells[2].text.strip()
    assert "IRREGULAR_TABLE" not in structures[0].findings
def test_one_row_or_invalid_bbox_native_candidates_are_weak():
    one_row = SimpleNamespace(bbox=(0, 0, 100, 20), rows=(SimpleNamespace(cells=((0, 0, 50, 20), (50, 0, 100, 20))),))
    invalid_bbox = SimpleNamespace(bbox=None, rows=tuple(
        SimpleNamespace(cells=(None, None))
        for row in range(4)))
    assert _native_table_candidate_diagnostic(one_row)["decision"] == "weak_candidate"
    assert _native_table_candidate_diagnostic(invalid_bbox)["decision"] == "weak_candidate"
    no_repeated_columns = SimpleNamespace(bbox=(0, 0, 100, 100), rows=tuple(
        SimpleNamespace(cells=((row * 20, row * 20, row * 20 + 8, row * 20 + 8), None))
        for row in range(3)))
    diagnostic = _native_table_candidate_diagnostic(no_repeated_columns)
    assert diagnostic["decision"] == "weak_candidate"
    assert "X_EDGE_SUPPORT_ACROSS_ROWS_COUNT_0" in diagnostic["signals"]


def test_pdf_parser_preserves_order_layout_and_source_spans():
    parser = PdfDocumentParser(lambda _: [{"page_number": 1, "blocks": [
        {"text": "Part I", "bbox": [1, 2, 3, 4], "font_size": 18, "font": "Bold"},
        {"text": "A rule.", "bbox": [1, 20, 3, 30], "font_size": 10},
    ]}])
    parsed = parser.parse(Path("fixture.pdf"))
    assert [block.source_text for block in parsed.pages[0].blocks] == ["Part I", "A rule."]
    assert parsed.pages[0].blocks[0].bbox == (1.0, 2.0, 3.0, 4.0)
    assert parsed.pages[0].blocks[0].font_weight == "bold"
    assert parsed.pages[0].blocks[1].source_span.block_index == 1
    assert parsed.source_text == "Part I\nA rule."


def test_pdf_parser_carries_exact_table_cell_location_on_source_span():
    parser = PdfDocumentParser(lambda _: [{"page_number": 3, "blocks": [
        {"block_id": "p3-table-0-r1-c2", "text": "12", "bbox": [10, 20, 30, 40],
         "kind": "table_cell", "table_id": "p3-table-0", "row": 1, "column": 2},
    ]}])

    span = parser.parse(Path("fixture.pdf")).pages[0].blocks[0].source_span

    assert span.bbox == (10.0, 20.0, 30.0, 40.0)
    assert span.table_id == "p3-table-0"
    assert span.table_cell == "r1:c2"
    assert (span.char_start, span.char_end) == (0, 2)


def test_pdf_parser_assigns_detector_default_table_id_to_unlabeled_table_cells():
    parsed = PdfDocumentParser(lambda _: [{"blocks": [
        {"block_id": "cell-a", "text": "A", "bbox": [10, 20, 30, 30], "kind": "table_cell"},
        {"block_id": "cell-b", "text": "B", "bbox": [30, 20, 50, 30], "kind": "cell"},
    ]}]).parse(Path("fixture.pdf"))
    assert {block.source_span.table_id for block in parsed.pages[0].blocks} == {"table-1"}
    assert parsed.pages[0].layout_plan.table_zones[0].zone_id == "table-1"


def test_pdf_parser_orders_strong_two_column_page_left_to_right():
    parser = PdfDocumentParser(lambda _: [{"page_number": 4, "blocks": [
        {"block_id": "right-top", "text": "Right top", "bbox": [309, 100, 535, 140]},
        {"block_id": "right-bottom", "text": "Right bottom", "bbox": [309, 180, 535, 220]},
        {"block_id": "left-top", "text": "Left top", "bbox": [54, 100, 280, 140]},
        {"block_id": "left-bottom", "text": "Left bottom", "bbox": [54, 180, 280, 220]},
        {"block_id": "footer", "text": "Footer", "bbox": [54, 700, 535, 720]},
    ]}])

    parsed = parser.parse(Path("fixture.pdf"))
    page = parsed.pages[0]

    assert [block.block_id for block in page.blocks] == [
        "left-top", "left-bottom", "right-top", "right-bottom", "footer",
    ]
    assert [block.source_span.block_index for block in page.blocks] == [0, 1, 2, 3, 4]
    assert page.blocks[0].bbox == (54.0, 100.0, 280.0, 140.0)
    assert parsed.source_text == "Left top\nLeft bottom\nRight top\nRight bottom\nFooter"


def test_pdf_parser_preserves_line_boxes_as_analysis_metadata():
    source_lines = [{"bbox": [10, y, 40, y + 8]} for y in (10, 20, 30)]
    parsed = PdfDocumentParser(lambda _: [{"page_number": 1, "blocks": [{
        "block_id": "paragraph", "text": "Original paragraph text", "bbox": [10, 10, 40, 38],
        "lines": source_lines,
    }]}]).parse(Path("fixture.pdf"))

    block = parsed.pages[0].blocks[0]
    assert block.source_text == "Original paragraph text"
    assert block.line_boxes == ((10.0, 10.0, 40.0, 18.0), (10.0, 20.0, 40.0, 28.0),
                                (10.0, 30.0, 40.0, 38.0))


def test_form_page_uses_geometric_order_without_prose_column_profiles():
    parsed = PdfDocumentParser(lambda _: [{"page_number": 1, "page_classification": "form", "blocks": [
        {"block_id": "right", "text": "Field", "bbox": [60, 10, 90, 20]},
        {"block_id": "left", "text": "Label", "bbox": [10, 10, 40, 20]},
    ]}]).parse(Path("fixture.pdf"))

    page = parsed.pages[0]
    assert page.page_classification == "form"
    assert page.layout_plan.profiles == ()
    assert [block.block_id for block in page.blocks] == ["left", "right"]


def test_pdf_parser_keeps_single_column_table_like_page_order():
    parser = PdfDocumentParser(lambda _: [{"page_number": 1, "blocks": [
        {"block_id": "row-2-right", "text": "B2", "bbox": [180, 130, 250, 145]},
        {"block_id": "row-1-left", "text": "A1", "bbox": [54, 100, 124, 115]},
        {"block_id": "row-2-left", "text": "A2", "bbox": [54, 130, 124, 145]},
        {"block_id": "row-1-far-right", "text": "C1", "bbox": [306, 100, 376, 115]},
        {"block_id": "row-1-right", "text": "B1", "bbox": [180, 100, 250, 115]},
        {"block_id": "row-2-far-right", "text": "C2", "bbox": [306, 130, 376, 145]},
    ]}])

    parsed = parser.parse(Path("fixture.pdf"))

    assert [block.block_id for block in parsed.pages[0].blocks] == [
        "row-1-far-right", "row-1-left", "row-1-right", "row-2-far-right",
        "row-2-left", "row-2-right",
    ]


def test_pdf_parser_joins_spans_by_bbox_without_merging_table_cells():
    parser = PdfDocumentParser(lambda _: [{"page_number": 31, "blocks": [
        {"block_id": "wizard-table", "bbox": [54, 401, 535, 660], "lines": [
            {"spans": [
                {"text": "Level", "bbox": [58, 411, 76, 421]},
                {"text": "Proficiency", "bbox": [85, 411, 124, 421]},
                {"text": "Bonus", "bbox": [127, 411, 163, 421]},
            ]},
            {"spans": [
                {"text": "1st", "bbox": [59, 430, 75, 440]},
                {"text": "+2", "bbox": [93, 430, 106, 440]},
                {"text": "Spellcasting", "bbox": [128, 430, 188, 440]},
            ]},
        ]},
    ]}])

    parsed = parser.parse(Path("fixture.pdf"))

    assert parsed.pages[0].blocks[0].source_text == "Level Proficiency Bonus\n1st +2 Spellcasting"
    assert parsed.source_text == parsed.pages[0].blocks[0].source_text
