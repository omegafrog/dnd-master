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
