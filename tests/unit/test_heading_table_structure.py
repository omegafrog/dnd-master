from dataclasses import replace

from preprocessing_agent.domain import ParsedBlock, ParsedDocument, ParsedPage, SourceSpan
from preprocessing_agent.domain.layout import BoundingBox, PageGeometry, ReadingOrderPlan
from preprocessing_agent.domain.serialization import to_dict
from preprocessing_agent.chunking import ChunkAssembler, ChunkPlanner, ChunkPolicy, ChunkSplitter
from preprocessing_agent.structure import HeadingAssociator, DocumentTreeBuilder, HeadingDetector, TableStructureDetector, document_reference_font_size


def block(identifier, text, bbox, **extra):
    return {"block_id": identifier, "text": text, "bbox": bbox, **extra}


def test_heading_association_is_limited_to_same_column_or_spanning_content():
    blocks = [
        block("h", "Combat", (10, 10, 40, 25), font_size=18, font="Bold"),
        block("same", "Same column", (10, 35, 40, 50)),
        block("other", "Other column", (60, 35, 90, 50)),
        block("span", "Shared", (10, 60, 90, 75)),
    ]
    association = HeadingAssociator().associate(blocks)[0]
    assert association.heading_block_id == "h"
    assert association.associated_block_ids == ("same", "span")
    assert "same_column" in association.evidence
    assert "spanning_content" in association.evidence


def test_table_structure_preserves_headers_rows_cells_and_uncertainty():
    blocks = [
        block("h1", "Name", (10, 10, 50, 25), table_id="t", row=0, column=0, is_header=True),
        block("h2", "Value", (50, 10, 90, 25), table_id="t", row=0, column=1, is_header=True),
        block("r1", "Dex", (10, 25, 50, 40), table_id="t", row=1, column=0),
        block("r2", "12", (50, 25, 90, 40), table_id="t", row=1, column=1),
        block("r3", "broken", (10, 40, 90, 55), table_id="t", row=2, column=0, column_span=2, merged=True),
    ]
    table = TableStructureDetector().detect(blocks)[0]
    assert table.table_id == "t"
    assert len(table.header_rows) == 1
    assert len(table.rows) == 2
    assert table.rows[1].cells[0].merged is True
    assert table.rows[1].cells[0].column_span == 2
    assert table.uncertain_cell_ids == ()
    assert table.bbox == BoundingBox(10, 10, 90, 55)
    assert all(cell.bbox.within(PageGeometry(100, 100)) for cell in table.cells)


def test_irregular_table_marks_uncertain_cell_and_finding():
    blocks = [
        block("a", "A", (10, 10, 40, 25), table_id="t", row=0, column=0, is_header=True),
        block("b", "B", (40, 10, 70, 25), table_id="t", row=0, column=1, is_header=True),
        block("c", "C", (10, 25, 40, 40), table_id="t", row=1, column=0),
    ]
    table = TableStructureDetector().detect(blocks)[0]
    assert table.uncertain_cell_ids == ("c",)
    assert "IRREGULAR_TABLE" in table.findings


def test_duplicate_native_table_projection_is_deduplicated_before_structuring():
    cell = block("cell-1", "Name", (0, 0, 20, 10), kind="table_cell", table_id="t", row=0, column=0)
    duplicate = {**cell}
    table = TableStructureDetector().detect([cell, duplicate])[0]
    assert [item.cell_id for item in table.cells] == ["cell-1"]


def test_heading_and_table_contracts_round_trip_through_json_schema():
    jsonschema = __import__("jsonschema")
    from pathlib import Path
    association = HeadingAssociator().associate([block("h", "Rules", (0, 0, 100, 20), font_size=18)])[0]
    table = TableStructureDetector().detect([block("c", "A", (0, 0, 20, 20), table_id="t")])[0]
    for name, value in (("heading-association.schema.json", association), ("heading-table.schema.json", table)):
        schema = __import__("json").loads((Path("schemas") / name).read_text())
        jsonschema.Draft202012Validator(schema).validate(to_dict(value))


def test_document_relative_heading_style_and_heading_candidate_evidence_are_preserved():
    pages = [{"geometry": {"height": 800}, "blocks": [
        {"block_id": "body", "text": "Body text with enough words to represent the main style.", "bbox": [20, 100, 220, 130],
         "lines": [{"text": "Body text with enough words to represent the main style.", "bbox": [20, 100, 220, 130],
                    "spans": [{"text": "Body text with enough words to represent the main style.", "size": 10}]}]},
        {"text": "RULES\nThe rule explanation continues here.", "bbox": [20, 150, 220, 180], "block_id": "p1-title-and-body",
         "lines": [
             {"text": "RULES", "bbox": [20, 150, 80, 160], "spans": [{"text": "RULES", "size": 12, "font": "Lora-Bold"}]},
             {"text": "The rule explanation continues here.", "bbox": [20, 168, 220, 180], "spans": [{"text": "The rule explanation continues here.", "size": 10, "font": "Lora-Regular"}]},
         ]},
    ]}]
    reference_size = document_reference_font_size(pages)
    associations, candidates = HeadingAssociator().analyze(pages[0]["blocks"], reference_font_size=reference_size)

    assert reference_size == 10
    assert [(item.text, item.accepted) for item in candidates] == [("Body text with enough words to represent the main style.", False), ("RULES", True), ("The rule explanation continues here.", False)]
    assert associations[0].heading_text == "RULES"
    assert associations[0].source_char_start == 0
    assert associations[0].source_char_end == 5
    assert associations[0].heading_block_id == "p1-title-and-body"

    page_block = ParsedBlock("p1-title-and-body", "RULES\nThe rule explanation continues here.",
        SourceSpan(1, 0), (20, 150, 220, 180), font_size=12)
    page = ParsedPage(1, (page_block,), page_block.source_text, associations,
        heading_candidates=candidates)
    tree = DocumentTreeBuilder().build(ParsedDocument("doc", "rules.pdf", page.source_text, (page,)))
    assert tree.root.children[0].title == "RULES"
    assert tree.root.children[0].block_ids == ("p1-title-and-body",)


def test_spatially_separated_same_size_line_can_be_a_heading_candidate():
    block_value = block("p1-combined", "Overview\nThe actual rule text follows.", (10, 10, 200, 45), lines=[
        {"text": "Overview", "bbox": (10, 10, 60, 20), "spans": [{"text": "Overview", "size": 10}]},
        {"text": "The actual rule text follows.", "bbox": (10, 29, 200, 39), "spans": [{"text": "The actual rule text follows.", "size": 10}]},
    ])
    associations, candidates = HeadingAssociator().analyze([block_value], reference_font_size=10)
    assert associations[0].heading_text == "Overview"
    assert "spatial_separation" in candidates[0].evidence
    assert candidates[0].source_char_start == 0 and candidates[0].source_char_end == 8


def test_multiple_headings_in_one_source_block_split_section_and_chunk_ranges_once(monkeypatch, caplog):
    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    caplog.set_level("INFO")
    source = "Main Heading\nOpening rule.\nSubheading\nFurther rule."
    raw = block("combined", source, (10, 10, 220, 75), lines=[
        {"text": "Main Heading", "bbox": (10, 10, 110, 20), "spans": [{"text": "Main Heading", "size": 14, "font": "Bold"}]},
        {"text": "Opening rule.", "bbox": (10, 23, 120, 33), "spans": [{"text": "Opening rule.", "size": 10, "font": "Regular"}]},
        {"text": "Subheading", "bbox": (10, 43, 90, 53), "spans": [{"text": "Subheading", "size": 12, "font": "Bold"}]},
        {"text": "Further rule.", "bbox": (10, 56, 120, 66), "spans": [{"text": "Further rule.", "size": 10, "font": "Regular"}]},
    ])
    associations, candidates = HeadingAssociator().analyze([raw], reference_font_size=10)
    assert [item.heading_text for item in associations] == ["Main Heading", "Subheading"]
    assert [(item.source_char_start, item.source_char_end) for item in associations] == [(0, 12), (27, 37)]
    associations = (replace(associations[0], hierarchy_resolved=True),
                    replace(associations[1], level=2, hierarchy_resolved=True))

    span = SourceSpan(1, 0, 0, len(source), bbox=(10, 10, 220, 75))
    parsed = ParsedBlock("combined", source, span, (10, 10, 220, 75), 14)
    page = ParsedPage(1, (parsed,), source, associations, heading_candidates=candidates)
    document = ParsedDocument("doc", "rules.pdf", source, (page,))
    tree = DocumentTreeBuilder().build(document)
    main = tree.root.children[0]
    subheading = main.children[0]
    assert main.title == "Main Heading"
    assert subheading.title == "Subheading"
    assert main.source_spans[0].char_start == 0
    assert main.source_spans[0].char_end == 12
    assert subheading.source_spans[0].char_start == 27
    assert subheading.source_spans[0].char_end == 37
    assert [segment.source_text for segment in main.source_segments] == ["Opening rule."]
    assert [segment.source_text for segment in subheading.source_segments] == ["Further rule."]

    chunks = ChunkPlanner().plan(tree, document)
    assert [chunk.source_text for chunk in chunks] == ["Opening rule.", "Further rule."]
    assert all("Main Heading" not in chunk.source_text and "Subheading" not in chunk.source_text for chunk in chunks)
    assert [chunk.source_spans[0].char_start for chunk in chunks] == [13, 38]
    assert "treeRangeNodeCount=3 legacyBlockNodeCount=0 plannedSourceSegmentCount=2 tableSourceSegmentExclusionCount=0 unresolvedHeadingPathOmissionCount=0 candidateCount=2" in caplog.text
    assembled = ChunkAssembler(ChunkSplitter(ChunkPolicy())).assemble(chunks)
    assert [chunk.source_text for chunk in assembled] == ["Opening rule.", "Further rule."]
    assert [chunk.source_spans[0].char_start for chunk in assembled] == [13, 38]
    recovered = [(main.source_spans[0].char_start, main.source_spans[0].char_end)]
    recovered.extend((segment.source_span.char_start, segment.source_span.char_end) for segment in main.source_segments)
    recovered.append((subheading.source_spans[0].char_start, subheading.source_spans[0].char_end))
    recovered.extend((segment.source_span.char_start, segment.source_span.char_end) for segment in subheading.source_segments)
    assert sorted(recovered) == [(0, 12), (13, 26), (27, 37), (38, len(source))]


def test_named_section_reference_sentence_requires_heading_layout_evidence():
    reference = ParsedBlock("reference", "Chapter 11: Creatures.", SourceSpan(1),
                            (10, 10, 130, 20), font_size=10)
    assert HeadingDetector().detect(reference, reference_font_size=10).is_heading is False


def test_heading_candidates_skip_explicit_table_cells():
    cells = [block("cell", "Attack", (10, 10, 50, 20), kind="table_cell", table_id="t",
                   row=0, column=0, font_size=18, font="Bold")]
    associations, candidates = HeadingAssociator().analyze(cells, reference_font_size=10)
    assert associations == ()
    assert candidates == ()


def test_table_schema_rejects_negative_row_index():
    jsonschema = __import__("jsonschema")
    from pathlib import Path
    schema = __import__("json").loads((Path("schemas") / "heading-table.schema.json").read_text())
    invalid = {"table_id": "t", "bbox": {"x0": 0, "y0": 0, "x1": 10, "y1": 10}, "header_rows": [], "rows": [{"row_index": -1, "cells": [], "header": False}], "cells": [], "merged_cell_ids": [], "uncertain_cell_ids": [], "findings": [], "confidence": 1}
    try:
        jsonschema.Draft202012Validator(schema).validate(invalid)
    except jsonschema.ValidationError:
        return
    raise AssertionError("negative row index should be rejected")
