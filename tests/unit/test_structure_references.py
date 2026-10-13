from preprocessing_agent.domain import ParsedBlock, ParsedDocument, ParsedPage, SourceSpan
from preprocessing_agent.structure import HeadingAssociation
from preprocessing_agent.structure.references import (
    collect_document_structure_references,
    extract_printed_contents_entries,
)


def test_printed_contents_candidates_keep_target_and_indentation_without_confirming_hierarchy():
    raw_pages = ({"page_number": 3, "blocks": ({"lines": (
        {"text": "Chapter One .................. 4", "bbox": (30, 10, 300, 20)},
        {"text": "    First Topic .............. 5", "bbox": (50, 25, 300, 35)},
        {"text": "Second Topic ................. 6", "bbox": (30, 40, 300, 50)},
    )},)},)

    entries = extract_printed_contents_entries(raw_pages)

    assert [entry["title"] for entry in entries] == ["Chapter One", "First Topic", "Second Topic"]
    assert entries[1]["target_printed_page"] == 5
    assert entries[1]["indentation"] > entries[0]["indentation"]


def test_reference_matches_are_evidence_only_and_keep_bookmark_parent():
    block = ParsedBlock("p2-b1", "First Topic", SourceSpan(2, 0, 0, 11))
    association = HeadingAssociation("p2-b1", 2, (), (), 1.0, heading_text="First Topic",
                                     source_char_start=0, source_char_end=11)
    document = ParsedDocument("doc", "fixture.pdf", block.source_text,
                              (ParsedPage(2, (block,), block.source_text,
                                          heading_associations=(association,)),))
    bookmarks = (
        {"level": 1, "title": "Chapter One", "target_page": 1},
        {"level": 2, "title": "First Topic", "target_page": 2},
    )
    contents = ({"reference_id": "contents-p3-b0-l1", "source": "printed_contents",
                 "source_page": 3, "title": "First Topic", "target_printed_page": 5,
                 "source_text": "First Topic ........ 5", "bbox": [50.0, 25.0, 300.0, 35.0],
                 "indentation": 50.0},)

    analyzed = collect_document_structure_references(document, bookmarks, contents)
    references = analyzed.metadata["document_structure_references"]
    candidate = references["heading_reference_candidates"][0]

    assert references["hierarchy_changed"] is False
    assert references["pdf_bookmarks"][1]["parent_reference_id"] == references["pdf_bookmarks"][0]["reference_id"]
    assert {item["source"] for item in candidate["references"]} == {"pdf_bookmark", "printed_contents"}
    assert all(item["relationship_confirmed"] is False for item in candidate["references"])
    bookmark_match = next(item for item in candidate["references"] if item["source"] == "pdf_bookmark")
    assert bookmark_match["page_distance"] == 0


def test_reference_text_matching_preserves_non_latin_titles():
    title = "공격과 방어"
    block = ParsedBlock("p1-b1", title, SourceSpan(1, 0, 0, len(title)))
    association = HeadingAssociation("p1-b1", 1, (), (), 1.0, heading_text=title,
                                     source_char_start=0, source_char_end=len(title))
    document = ParsedDocument("doc", "fixture.pdf", title,
                              (ParsedPage(1, (block,), title, heading_associations=(association,)),))

    analyzed = collect_document_structure_references(
        document, ({"level": 1, "title": title, "target_page": 1},)
    )

    candidate = analyzed.metadata["document_structure_references"]["heading_reference_candidates"][0]
    assert candidate["references"][0]["title_similarity"] == 1.0
