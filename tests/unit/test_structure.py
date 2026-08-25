from pathlib import Path

from preprocessing_agent.parsers.pdf import PdfDocumentParser
from preprocessing_agent.structure import DocumentTreeBuilder, HeadingDetector


def test_heading_features_and_tree_keep_document_order():
    parsed = PdfDocumentParser(lambda _: [{"blocks": [
        {"text": "1. Player's Handbook", "font_size": 18, "font": "Bold"},
        {"text": "2. Combat", "font_size": 16, "font": "Bold"},
        {"text": "2.1 Making an Attack", "font_size": 14, "font": "Bold"},
        {"text": "Use the action rules."},
    ]}]).parse(Path("fixture.pdf"))
    assert HeadingDetector().detect(parsed.pages[0].blocks[0]).level == 1
    tree = DocumentTreeBuilder().build(parsed)
    assert [node.title for node in tree.root.children] == ["1. Player's Handbook", "2. Combat"]
    assert tree.root.children[1].children[0].title == "2.1 Making an Attack"
    assert tree.root.children[1].children[0].block_ids == ("p1-b2", "p1-b3")
