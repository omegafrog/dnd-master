from pathlib import Path

from preprocessing_agent.parsers.pdf import PdfDocumentParser
from preprocessing_agent.structure import DocumentTreeBuilder


def test_fixture_parse_to_tree_boundary():
    parser = PdfDocumentParser(lambda _: [{"blocks": [
        {"text": "Part I", "font_size": 20, "font": "Bold"},
        {"text": "Chapter 1", "font_size": 18, "font": "Bold"},
        {"text": "Section Rules", "font_size": 16, "font": "Bold"},
        {"text": "Subsection Advantage", "font_size": 14, "font": "Bold"},
        {"text": "Roll two dice."},
    ]}])
    tree = DocumentTreeBuilder().build(parser.parse(Path("dnd-fixture.pdf")))
    part = tree.root.children[0]
    assert part.title == "Part I"
    assert part.children[0].title == "Chapter 1"
    assert part.children[0].children[0].children[0].title == "Subsection Advantage"
