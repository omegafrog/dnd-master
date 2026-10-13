from preprocessing_agent.domain import ParsedBlock, ParsedDocument, ParsedPage, SourceSpan
from preprocessing_agent.structure import (
    DocumentHierarchyResolver,
    DocumentTreeBuilder,
    HeadingAssociation,
    HeadingCandidate,
)
from preprocessing_agent.validation.layout import LayoutValidationService


def _page(number, title, size, level=1, numbered=False):
    block_id = f"p{number}-b1"
    block = ParsedBlock(block_id, title, SourceSpan(number, 0, 0, len(title)),
                        (10, 80, 200, 100), font_size=size)
    association = HeadingAssociation(block_id, level, (), ("numbered",) if numbered else (), .9, heading_text=title,
                                     source_char_start=0, source_char_end=len(title))
    candidate = HeadingCandidate(block_id, 0, (0,), 0, len(title), title,
                                 (10, 80, 200, 100), size, "Lora-Regular", .9,
                                 True, "heading", ("relative_font_size",))
    return ParsedPage(number, (block,), title, (association,), heading_candidates=(candidate,))


def test_explicit_numbering_resolves_parent_while_repeated_style_is_not_enough(monkeypatch, caplog):
    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    caplog.set_level("INFO")
    pages = (_page(1, "Main", 18, 1, True), _page(2, "First subsection", 12, 2, True),
             _page(3, "Second subsection", 12),
             _page(4, "Known subsection after uncertainty", 12, 2, True),
             _page(5, "Next main", 18, 1, True))
    document = ParsedDocument("doc", "rules.pdf", "", pages)

    resolved = DocumentHierarchyResolver().resolve(document)
    assert [page.heading_associations[0].hierarchy_resolved for page in resolved.pages] == [True, True, False, False, True]
    assert resolved.pages[1].heading_associations[0].parent_heading_block_id == "p1-b1"
    assert resolved.pages[2].heading_associations[0].ambiguous is False
    assert resolved.pages[2].heading_associations[0].findings == ()
    assert resolved.pages[3].heading_associations[0].parent_heading_block_id is None

    tree = DocumentTreeBuilder().build(resolved)
    assert [node.title for node in tree.root.children] == ["Main", "Second subsection", "Known subsection after uncertainty", "Next main"]
    assert [node.title for node in tree.root.children[0].children] == ["First subsection"]
    assert not tree.root.children[1].hierarchy_resolved
    assert "repeatedStyleCount=2" in caplog.text


def test_same_style_headings_remain_unresolved_and_are_not_nested_as_guesses(monkeypatch, caplog):
    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    caplog.set_level("INFO")
    document = ParsedDocument("doc", "rules.pdf", "", (_page(1, "First", 12), _page(2, "Second", 12)))

    resolved = DocumentHierarchyResolver().resolve(document)
    assert all(not page.heading_associations[0].hierarchy_resolved for page in resolved.pages)
    assert all(page.heading_associations[0].ambiguous is False for page in resolved.pages)
    assert all(page.heading_associations[0].findings == () for page in resolved.pages)
    assert all("style_does_not_uniquely_resolve_level" in page.heading_associations[0].hierarchy_evidence
               for page in resolved.pages)
    tree = DocumentTreeBuilder().build(resolved)
    assert [node.title for node in tree.root.children] == ["First", "Second"]
    assert not tree.root.children[0].hierarchy_resolved
    assert "resolvedHeadingCount=0" in caplog.text
    assert "unresolvedHeadingCount=2" in caplog.text
    assert "recoveryAction=preserve_unresolved_for_review" in caplog.text

    result = LayoutValidationService().validate({
        "page_number": 1,
        "geometry": {"width": 200, "height": 200},
        "blocks": [{"block_id": "p1-b1", "bbox": [10, 80, 100, 100]}],
        "layout": {"ordered_block_ids": ["p1-b1"], "furniture_block_ids": []},
        "heading_associations": [{"ambiguous": False, "findings": [], "hierarchy_resolved": False,
                                   "confidence": 1.0}],
    }, {"page_number": 1, "sha256": "source-render-digest"})
    assert "HEADING_ASSOCIATION_AMBIGUOUS" not in {finding.code for finding in result.findings}
