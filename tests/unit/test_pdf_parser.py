from pathlib import Path

from preprocessing_agent.parsers.pdf import PdfDocumentParser


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
