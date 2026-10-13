from pathlib import Path

from preprocessing_agent.chunking import ChunkAssembler, ChunkPlanner, ChunkPolicy, ChunkSplitter
from preprocessing_agent.domain import ContentType
from preprocessing_agent.parsers.pdf import PdfDocumentParser
from preprocessing_agent.structure import DocumentTreeBuilder, HeadingAssociation, HeadingDetector
from preprocessing_agent.domain import ParsedBlock, ParsedDocument, ParsedPage, SourceSpan
from preprocessing_agent.validation import ValidationPolicy, validate_chunks


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
            {"text": "45 Chapter 4: Personality and Background", "bbox": [360, 741, 576, 757], "font_size": 9},
            {"text": "D&D Basic Rules (Version 1.0). Not for resale.", "bbox": [128, 764, 457, 770], "font_size": 6},
        ]},
    ]).parse(Path("fixture.pdf"))

    tree = DocumentTreeBuilder().build(parsed)

    assert [node.title for node in tree.root.children] == ["Soldier"]
    assert tree.root.children[0].block_ids == ("p44-b1", "p44-b2", "p44-b3")


def test_repeated_furniture_keeps_table_cells_and_only_skips_matching_text_at_page_edges(monkeypatch, caplog):
    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    caplog.set_level("INFO")
    pages = []
    for page_number in (1, 2):
        blocks = (
            ParsedBlock(f"edge-die-{page_number}", "d6", SourceSpan(page_number, 0),
                        (30, 30, 45, 42), font_size=8),
            ParsedBlock(f"table-roll-{page_number}", "20",
                        SourceSpan(page_number, 1, table_id=f"t{page_number}", table_cell="r0:c0"),
                        (30, 40, 45, 52)),
            ParsedBlock(f"body-die-{page_number}", "d20 is rolled here.", SourceSpan(page_number, 2),
                        (30, 100, 180, 115), font_size=8),
        )
        pages.append(ParsedPage(page_number, blocks, "\n".join(block.source_text for block in blocks)))

    tree = DocumentTreeBuilder().build(ParsedDocument("doc", "rules.pdf", "", tuple(pages)))

    assert {"table-roll-1", "table-roll-2", "body-die-1", "body-die-2"} <= set(tree.root.block_ids)
    assert "edge-die-1" not in tree.root.block_ids
    assert "edge-die-2" not in tree.root.block_ids
    assert "tableCellPreservedCount=2" in caplog.text
    assert "repeatedFurnitureSkippedCount=2" in caplog.text


def test_body_after_unresolved_heading_keeps_local_ownership_without_inventing_path(monkeypatch, caplog):
    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    caplog.set_level("INFO")
    heading = ParsedBlock("heading", "Unresolved Heading", SourceSpan(1, 0, 0, 18), (20, 20, 170, 34), 12)
    body_text = "This rule remains attached to the visible heading."
    body = ParsedBlock("body", body_text, SourceSpan(1, 1, 0, len(body_text)), (20, 45, 280, 60), 10)
    association = HeadingAssociation(
        "heading", 1, (), ("document_style",), 0.9, heading_text=heading.source_text,
        source_char_start=0, source_char_end=len(heading.source_text), hierarchy_resolved=False,
    )
    page = ParsedPage(1, (heading, body), heading.source_text + "\n" + body_text,
                      heading_associations=(association,))
    document = ParsedDocument("doc", "rules.pdf", page.source_text, (page,))

    tree = DocumentTreeBuilder().build(document)
    unresolved_heading = tree.root.children[0]
    assert unresolved_heading.hierarchy_resolved is False
    assert [segment.source_text for segment in unresolved_heading.source_segments] == [heading.source_text, body_text]
    assert tree.root.source_segments == ()

    candidates = ChunkPlanner().plan(tree, document)
    chunks = ChunkAssembler(ChunkSplitter(ChunkPolicy(min_tokens=1))).assemble(candidates)
    assert len(chunks) == 1
    assert chunks[0].section_path == ()
    validation = validate_chunks(chunks, tree=tree, document=document,
                                 policy=ValidationPolicy(min_tokens=1))
    assert not [issue for issue in validation.issues if issue.issue_type in {"missing_section_path", "orphan_heading"}]
    assert "unresolvedPageOwnedBlockCount=1" in caplog.text


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
        HeadingAssociation("h", 2, ("b",), (), 1.0, hierarchy_resolved=True),
    ))
    tree = DocumentTreeBuilder().build(ParsedDocument("doc", "rules.pdf", page.source_text, (page,)))

    section = tree.root.children[0]
    assert section.title == "Ordinary label"
    assert section.level == 2
    assert section.block_ids == ("h", "b")
    assert "dev_document_tree stage=complete documentId=doc" in caplog.text
    assert "associationCount=1 multiHeadingBlockCount=0" in caplog.text


def test_low_font_body_at_page_boundary_remains_in_open_section(monkeypatch, caplog):
    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    caplog.set_level("INFO")
    heading = ParsedBlock("heading", "Character Creation", SourceSpan(1, 0, 0, 18),
                          (36, 100, 200, 115), font_size=14)
    first_page = ParsedPage(1, (heading,), heading.source_text,
                            (HeadingAssociation("heading", 1, (), (), 1.0, hierarchy_resolved=True, heading_text="Character Creation",
                                                source_char_start=0, source_char_end=18),))
    top_body = ParsedBlock("top-body", "The next step begins here.", SourceSpan(2, 0, 0, 26),
                           (36, 46, 575, 74), font_size=9)
    bottom_body = ParsedBlock("bottom-body", "The last instruction continues.", SourceSpan(2, 1, 27, 59),
                              (36, 700, 575, 731), font_size=9)
    page_marker = ParsedBlock("page-marker", "Page 2", SourceSpan(2, 2), (560, 750, 575, 760), font_size=8)
    second_page = ParsedPage(2, (top_body, bottom_body, page_marker),
                             top_body.source_text + "\n" + bottom_body.source_text + "\nPage 2")

    tree = DocumentTreeBuilder().build(ParsedDocument("doc", "rules.pdf", "", (first_page, second_page)))

    section = tree.root.children[0]
    assert section.title == "Character Creation"
    assert [segment.source_text for segment in section.source_segments] == [
        "Character Creation", top_body.source_text, bottom_body.source_text,
    ]
    assert "multiHeadingBlockCount=0" in caplog.text
    assert "pageMarkerSkippedCount=1 sourceSegmentCount=3" in caplog.text
