from pathlib import Path
from types import SimpleNamespace

from preprocessing_agent.parsers.pdf import PdfDocumentParser, _is_reliable_native_table, _native_table_candidate_diagnostic


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
    assert _is_reliable_native_table(weak_missing_cell) is False


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
