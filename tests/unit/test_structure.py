from pathlib import Path

from preprocessing_agent.parsers.pdf import PdfDocumentParser
from preprocessing_agent.structure import DocumentTreeBuilder, HeadingAssociation, HeadingDetector
from preprocessing_agent.domain import ParsedBlock, ParsedDocument, ParsedPage, SourceSpan


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


def test_heading_detection_excludes_page_furniture_and_fake_numeric_rows_but_keeps_layout_heading():
    parsed = PdfDocumentParser(lambda _: [
        {"page_number": 44, "blocks": [
            {"text": "44 Chapter 4: Personality and Background", "bbox": [360, 741, 576, 757], "font_size": 9},
            {"text": "Soldier", "bbox": [54, 32, 106, 49], "font_size": 15, "font": "MrsEavesSmallCaps"},
            {"text": "1. This is an ordinary numbered sentence.", "bbox": [54, 80, 280, 95], "font_size": 9},
            {"text": "War has been your life.", "bbox": [54, 100, 280, 125], "font_size": 9},
            {"text": "D&D Basic Rules (Version 1.0). Not for resale.", "bbox": [128, 764, 457, 770], "font_size": 6},
        ]},
        {"page_number": 45, "blocks": [
            {"text": "D&D Basic Rules (Version 1.0). Not for resale.", "bbox": [128, 764, 457, 770], "font_size": 6},
        ]},
    ]).parse(Path("fixture.pdf"))

    tree = DocumentTreeBuilder().build(parsed)

    assert [node.title for node in tree.root.children] == ["Soldier"]
    assert tree.root.children[0].block_ids == ("p44-b1", "p44-b2", "p44-b3")


def test_heading_detection_does_not_promote_numeric_table_values_or_page_numbers():
    parsed = PdfDocumentParser(lambda _: [{"page_number": 9, "blocks": [
        {"text": "9", "bbox": [572, 739, 577, 765], "font_size": 9},
        {"text": "2–3\n-4", "bbox": [330, 588, 394, 612]},
    ]}]).parse(Path("fixture.pdf"))

    detector = HeadingDetector()
    assert detector.detect(parsed.pages[0].blocks[0]).is_heading is False
    assert detector.detect(parsed.pages[0].blocks[1]).is_heading is False


def test_large_stat_block_title_remains_confident_with_separator_text():
    parsed = PdfDocumentParser(lambda _: [{"blocks": [
        {"text": "Giant Inferno Spider\nLarge Monstrosity, Unaligned\n-----", "bbox": [39, 40, 280, 122], "font_size": 14},
    ]}]).parse(Path("fixture.pdf"))

    decision = HeadingDetector().detect(parsed.pages[0].blocks[0])

    assert decision.is_heading is True
    assert decision.confidence >= 0.8


def test_tree_uses_precomputed_heading_association_without_redetecting_heading(monkeypatch, caplog):
    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    caplog.set_level("INFO")
    heading = ParsedBlock("h", "Ordinary label", SourceSpan(1, 0))
    body = ParsedBlock("b", "Associated rule.", SourceSpan(1, 1))
    page = ParsedPage(1, (heading, body), "Ordinary label\nAssociated rule.", (
        HeadingAssociation("h", 2, ("b",), (), 1.0),
    ))
    tree = DocumentTreeBuilder().build(ParsedDocument("doc", "rules.pdf", page.source_text, (page,)))

    section = tree.root.children[0]
    assert section.title == "Ordinary label"
    assert section.level == 2
    assert section.block_ids == ("h", "b")
    assert "dev_document_tree stage=complete documentId=doc" in caplog.text
    assert "associationCount=1 detectorFallbackPages=0" in caplog.text
