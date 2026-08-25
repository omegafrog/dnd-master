from preprocessing_agent.domain import ParsedBlock, SourceSpan
from preprocessing_agent.domain.layout import BoundingBox, PageGeometry, ReadingOrderPlan
from preprocessing_agent.domain.serialization import to_dict
from preprocessing_agent.structure import HeadingAssociator, TableStructureDetector


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


def test_heading_and_table_contracts_round_trip_through_json_schema():
    jsonschema = __import__("jsonschema")
    from pathlib import Path
    association = HeadingAssociator().associate([block("h", "Rules", (0, 0, 100, 20), font_size=18)])[0]
    table = TableStructureDetector().detect([block("c", "A", (0, 0, 20, 20), table_id="t")])[0]
    for name, value in (("heading-association.schema.json", association), ("heading-table.schema.json", table)):
        schema = __import__("json").loads((Path("schemas") / name).read_text())
        jsonschema.Draft202012Validator(schema).validate(to_dict(value))
