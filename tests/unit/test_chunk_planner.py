from preprocessing_agent.chunking import ChunkAssembler, ChunkPlanner, ChunkPolicy, ChunkSplitter
from preprocessing_agent.domain import Chunk, ContentType, ParsedBlock, ParsedDocument, ParsedPage, SectionNode, SourceSegment, SourceSpan, DocumentTree
from preprocessing_agent.domain.layout import BoundingBox
from preprocessing_agent.structure import TableCell, TableRow, TableStructure
from preprocessing_agent.pipeline.pipeline import _document_prose, _recover_uncovered_text


def test_planner_creates_semantic_candidates_before_token_splitting():
    span = SourceSpan(1, block_index=0)
    block = ParsedBlock("b1", "A rule paragraph.", span)
    document = ParsedDocument("doc", "rules.pdf", block.source_text, (ParsedPage(1, (block,), block.source_text),))
    section = SectionNode("s1", "Combat", 1, ContentType.RULE, block_ids=("b1",))
    candidates = ChunkPlanner().plan(DocumentTree("doc", SectionNode("root", "doc", 0, ContentType.NARRATIVE, children=(section,))), document)
    assert len(candidates) == 1
    assert candidates[0].canonical_key.startswith("combat.")
    assert ".p0001.b0000" in candidates[0].canonical_key
    assert candidates[0].source_text == block.source_text
    assert candidates[0].source_segments[0].source_text == block.source_text
    assert candidates[0].source_segments[0].source_span == span


def test_planner_does_not_put_unresolved_heading_in_section_path(monkeypatch, caplog):
    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    caplog.set_level("INFO")
    span = SourceSpan(1, block_index=0)
    block = ParsedBlock("b1", "Uncertain heading body.", span)
    document = ParsedDocument("doc", "rules.pdf", block.source_text,
                              (ParsedPage(1, (block,), block.source_text),))
    known_parent = SectionNode("s1", "Known chapter", 1, ContentType.RULE, children=(
        SectionNode("s2", "Uncertain heading", 2, ContentType.RULE,
                    block_ids=("b1",), hierarchy_resolved=False),
    ))

    candidates = ChunkPlanner().plan(
        DocumentTree("doc", SectionNode("root", "doc", 0, ContentType.NARRATIVE,
                                         children=(known_parent,))), document
    )

    assert len(candidates) == 1
    assert candidates[0].section_path == ("known_chapter",)
    assert candidates[0].source_text == block.source_text
    assert "unresolvedHeadingPathOmissionCount=1" in caplog.text


def test_planner_removes_duplicated_page_prefixes_from_canonical_keys():
    span = SourceSpan(75, block_index=0)
    block = ParsedBlock("b1", "Combat rules.", span)
    document = ParsedDocument("doc", "rules.pdf", block.source_text, (ParsedPage(75, (block,), block.source_text),))
    section = SectionNode("s1", "7575 Chapter 9: Combat", 1, ContentType.RULE, block_ids=("b1",))

    candidates = ChunkPlanner().plan(
        DocumentTree("doc", SectionNode("root", "doc", 0, ContentType.NARRATIVE, children=(section,))), document
    )

    assert candidates[0].canonical_key.startswith("chapter_9_combat.")
    assert ".p0075.b0000" in candidates[0].canonical_key


def test_planner_bounds_long_canonical_keys_with_stable_semantic_suffix():
    span = SourceSpan(1, block_index=0)
    block = ParsedBlock("b1", "A rule paragraph.", span)
    title = "Chapter " + ("very long semantic heading " * 30)
    document = ParsedDocument("doc", "rules.pdf", block.source_text, (ParsedPage(1, (block,), block.source_text),))
    section = SectionNode("s1", title, 1, ContentType.RULE, block_ids=("b1",))

    candidates = ChunkPlanner().plan(
        DocumentTree("doc", SectionNode("root", "doc", 0, ContentType.NARRATIVE, children=(section,))), document
    )

    key = candidates[0].canonical_key
    assert len(key) <= 160
    assert key.startswith("chapter_very_long_semantic_heading")
    assert key.endswith("_" + __import__("hashlib").sha256(("chapter_" + "very_long_semantic_heading_" * 30).strip("_").encode()).hexdigest()[:12])
    assert ".p0001.b0000" in key


def test_planner_preserves_table_row_and_column_relationships(monkeypatch, caplog):
    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    caplog.set_level("INFO")
    blocks = tuple(ParsedBlock(f"b{i}", text, SourceSpan(1, i, bbox=(i % 2 * 20, i // 2 * 20, i % 2 * 20 + 10, i // 2 * 20 + 10), table_id="t1", table_cell=f"r{i // 2}:c{i % 2}")) for i, text in enumerate(("Name", "Value", "Dex", "12")))
    cells = tuple(TableCell(f"b{i}", i // 2, i % 2, block.source_text, BoundingBox(i % 2 * 20, i // 2 * 20, i % 2 * 20 + 10, i // 2 * 20 + 10), source_block_ids=(block.block_id,)) for i, block in enumerate(blocks))
    header = TableRow(0, cells[:2], True)
    data = TableRow(1, cells[2:])
    table = TableStructure("t1", BoundingBox(0, 0, 40, 40), (header,), (data,), cells)
    page = ParsedPage(1, blocks, "Name\nValue\nDex\n12", tables=(table,))
    section = SectionNode("s1", "Attributes", 1, ContentType.RULE, block_ids=tuple(block.block_id for block in blocks))

    candidates = ChunkPlanner().plan(DocumentTree("doc", SectionNode("root", "doc", 0, ContentType.NARRATIVE, children=(section,))), ParsedDocument("doc", "rules.pdf", page.source_text, (page,)))

    assert len(candidates) == 1
    assert candidates[0].source_text == "Name, Value: Dex, 12."
    assert candidates[0].source_spans == tuple(block.source_span for block in blocks)
    chunk = ChunkAssembler(ChunkSplitter(ChunkPolicy())).assemble(candidates)[0]
    assert chunk.source_text == "Name, Value: Dex, 12."
    assert chunk.source_spans == candidates[0].source_spans
    assert tuple(span.table_cell for span in chunk.source_spans) == tuple(f"r{i // 2}:c{i % 2}" for i in range(4))
    assert "stage=table_source_mapping documentId=doc pageNumber=1 tableId=t1 expectedMappedCellCount=4 actualMappedCellCount=4 actualSourceSpanCount=4 outcome=success" in caplog.text
    assert "dev_chunk_planner stage=complete documentId=doc" in caplog.text
    assert "tableCount=1 treeRangeNodeCount=0 legacyBlockNodeCount=2 plannedSourceSegmentCount=0 tableSourceSegmentExclusionCount=0 unresolvedHeadingPathOmissionCount=0 candidateCount=1" in caplog.text


def test_table_blank_cells_do_not_break_source_mapping(monkeypatch, caplog):
    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    caplog.set_level("INFO")
    blocks = tuple(ParsedBlock(f"b{i}", text, SourceSpan(1, i, table_id="t1", table_cell=f"r0:c{i}"))
                   for i, text in enumerate(("Name", " ", "Value", "\t\n")))
    cells = tuple(TableCell(block.block_id, 0, i, block.source_text,
                            BoundingBox(i * 20, 0, i * 20 + 10, 10), source_block_ids=(block.block_id,))
                  for i, block in enumerate(blocks))
    table = TableStructure("t1", BoundingBox(0, 0, 80, 10), (TableRow(0, cells, True),), (), cells)
    page = ParsedPage(1, blocks, "\n".join(block.source_text for block in blocks), tables=(table,))
    root = SectionNode("root", "doc", 0, ContentType.RULE, block_ids=tuple(block.block_id for block in blocks))

    candidates = ChunkPlanner().plan(DocumentTree("doc", root), ParsedDocument("doc", "rules.pdf", page.source_text, (page,)))
    chunks = ChunkAssembler(ChunkSplitter(ChunkPolicy())).assemble(candidates)

    assert len(chunks) == 1
    assert chunks[0].source_text == "Name, Value."
    assert chunks[0].source_spans == (blocks[0].source_span, blocks[2].source_span)
    assert "expectedMappedCellCount=2 actualMappedCellCount=2 actualSourceSpanCount=2 outcome=success" in caplog.text


def test_document_prose_places_table_once_in_reading_order_and_joins_page_boundary():
    blocks = (
        ParsedBlock("before", "A rule continues", SourceSpan(1, 0)),
        ParsedBlock("h1", "Name", SourceSpan(1, 1, table_id="t1", table_cell="r0:c0")),
        ParsedBlock("h2", "Cost", SourceSpan(1, 2, table_id="t1", table_cell="r0:c1")),
        ParsedBlock("c1", "Potion", SourceSpan(1, 3, table_id="t1", table_cell="r1:c0")),
        ParsedBlock("c2", "50 gp", SourceSpan(1, 4, table_id="t1", table_cell="r1:c1")),
        ParsedBlock("after", "across pages.", SourceSpan(1, 5)),
    )
    cells = tuple(TableCell(block.block_id, row, column, block.source_text,
                            BoundingBox(column * 20, row * 20, column * 20 + 10, row * 20 + 10),
                            source_block_ids=(block.block_id,))
                  for block, row, column in zip(blocks[1:5], (0, 0, 1, 1), (0, 1, 0, 1)))
    table = TableStructure("t1", BoundingBox(0, 0, 40, 40),
                           (TableRow(0, cells[:2], True),), (TableRow(1, cells[2:]),), cells)
    page_one = ParsedPage(1, blocks, "\n".join(block.source_text for block in blocks), tables=(table,))
    page_two_block = ParsedBlock("next", "The next page starts here.", SourceSpan(2, 0))
    page_two = ParsedPage(2, (page_two_block,), page_two_block.source_text)
    document = ParsedDocument("doc", "rules.pdf", page_one.source_text + "\n" + page_two.source_text,
                              (page_one, page_two))

    prose, rendered_table_count = _document_prose(document)

    assert prose == "A rule continues Name, Cost: Potion, 50 gp. across pages. The next page starts here."
    assert rendered_table_count == 1
    assert "|" not in prose


def test_root_source_segments_exclude_structured_table_cells_and_emit_them_separately(monkeypatch, caplog):
    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    caplog.set_level("INFO")
    prose_span = SourceSpan(1, 0)
    table_span = SourceSpan(1, 1, table_id="t1", table_cell="r0:c0")
    prose = ParsedBlock("prose", "A nearby rule.", prose_span)
    table_block = ParsedBlock("table-cell", "Name", table_span)
    cell = TableCell("table-cell", 0, 0, "Name", BoundingBox(0, 0, 20, 10), source_block_ids=("table-cell",))
    table = TableStructure("t1", BoundingBox(0, 0, 20, 10), (TableRow(0, (cell,), True),), (), (cell,))
    page = ParsedPage(1, (prose, table_block), "A nearby rule.\nName", tables=(table,))
    root = SectionNode("root", "doc", 0, ContentType.UNKNOWN,
                       block_ids=("prose", "table-cell"),
                       source_segments=(SourceSegment(prose.source_text, prose_span),
                                        SourceSegment(table_block.source_text, table_span)))

    candidates = ChunkPlanner().plan(DocumentTree("doc", root), ParsedDocument("doc", "rules.pdf", page.source_text, (page,)))

    assert len(candidates) == 2
    prose_candidate = next(item for item in candidates if item.source_text == "A nearby rule.")
    table_candidate = next(item for item in candidates if item.content_type is ContentType.TABLE)
    assert prose_candidate.source_spans == (prose_span,)
    assert table_candidate.source_text == "Name."
    assert table_candidate.source_spans == (table_span,)
    assert "expectedTableSourceSegmentCount=1 actualExcludedSegmentCount=1" in caplog.text
    assert "tableSourceSegmentExclusionCount=1" in caplog.text


def test_table_intersecting_multiple_sections_is_emitted_once():
    blocks = (ParsedBlock("b1", "Name", SourceSpan(1, 0)), ParsedBlock("b2", "Value", SourceSpan(1, 1)))
    cells = tuple(TableCell(block.block_id, 0, index, block.source_text, BoundingBox(index * 20, 0, index * 20 + 10, 10), source_block_ids=(block.block_id,)) for index, block in enumerate(blocks))
    table = TableStructure("t1", BoundingBox(0, 0, 40, 10), (TableRow(0, cells, True),), (), cells)
    page = ParsedPage(1, blocks, "Name\nValue", tables=(table,))
    sections = tuple(SectionNode(f"s{index}", f"Section {index}", 1, ContentType.RULE, block_ids=(block.block_id,)) for index, block in enumerate(blocks))

    candidates = ChunkPlanner().plan(DocumentTree("doc", SectionNode("root", "doc", 0, ContentType.NARRATIVE, children=sections)), ParsedDocument("doc", "rules.pdf", page.source_text, (page,)))

    assert len(candidates) == 1
    assert candidates[0].content_type is ContentType.TABLE
    assert candidates[0].source_text == "Name, Value."


def test_planner_logs_table_skip_when_source_spans_are_missing(monkeypatch, caplog):
    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    caplog.set_level("INFO")
    cell = TableCell("missing", 0, 0, "Name", BoundingBox(0, 0, 10, 10), source_block_ids=("missing",))
    table = TableStructure("t-missing", BoundingBox(0, 0, 10, 10), (TableRow(0, (cell,), True),), (), (cell,))
    page = ParsedPage(1, (), "", tables=(table,))
    section = SectionNode("s1", "Attributes", 1, ContentType.RULE, block_ids=("missing",))

    candidates = ChunkPlanner().plan(DocumentTree("doc", SectionNode("root", "doc", 0, ContentType.NARRATIVE, children=(section,))), ParsedDocument("doc", "rules.pdf", "", (page,)))

    assert candidates == ()
    assert "stage=skip_table documentId=doc pageNumber=1 tableId=t-missing" in caplog.text
    assert "expectedCellCount=1 actualSerializedCellCount=1 expectedMappedCellCount=1 actualMappedCellCount=0 actualSourceSpanCount=0" in caplog.text
    assert "skipReason=no_source_spans recoveryAction=skip_table outcome=skipped" in caplog.text


def test_planner_skips_table_when_cell_text_cannot_be_mapped_to_its_source(monkeypatch, caplog):
    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    caplog.set_level("INFO")
    block = ParsedBlock("b1", "Other", SourceSpan(1, 0, table_id="t1", table_cell="r0:c0"))
    cell = TableCell("b1", 0, 0, "Name", BoundingBox(0, 0, 10, 10), source_block_ids=("b1",))
    table = TableStructure("t1", BoundingBox(0, 0, 10, 10), (TableRow(0, (cell,), True),), (), (cell,))
    page = ParsedPage(1, (block,), "Other", tables=(table,))
    section = SectionNode("s1", "Attributes", 1, ContentType.RULE, block_ids=("b1",))

    candidates = ChunkPlanner().plan(DocumentTree("doc", SectionNode("root", "doc", 0, ContentType.NARRATIVE, children=(section,))), ParsedDocument("doc", "rules.pdf", page.source_text, (page,)))

    assert candidates == ()
    assert "expectedMappedCellCount=1 actualMappedCellCount=0" in caplog.text
    assert "skipReason=unmapped_cell_source_text recoveryAction=skip_table outcome=skipped" in caplog.text


def test_uncovered_text_is_recovered_with_exact_range_and_section_owner(monkeypatch, caplog):
    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    caplog.set_level("INFO")
    text = "InventoryCharacters carry gear."
    block_span = SourceSpan(1, 0, 0, len(text))
    block = ParsedBlock("p1-b0", text, block_span)
    body_start = len("Inventory")
    body_span = SourceSpan(1, 0, body_start, len(text))
    section = SectionNode("s1", "Inventory", 1, ContentType.RULE,
                          source_spans=(SourceSpan(1, 0, 0, body_start),),
                          block_ids=(block.block_id,),
                          source_segments=(SourceSegment(text[body_start:], body_span),))
    tree = DocumentTree("doc", SectionNode("root", "doc", 0, ContentType.NARRATIVE, children=(section,)))
    document = ParsedDocument("doc", "rules.pdf", text, (ParsedPage(1, (block,), text),))
    existing = Chunk("chk-body", "inventory.body", ContentType.RULE, text[body_start:], text[body_start:], 4,
                     (body_span,), ("inventory",))

    chunks, accounting, issues = _recover_uncovered_text(document, tree, (existing,), ChunkPolicy())

    assert [chunk.source_text for chunk in chunks] == [text[body_start:], "Inventory"]
    assert chunks[1].source_spans == (SourceSpan(1, 0, 0, body_start),)
    assert chunks[1].section_path == ("inventory",)
    assert accounting["recovered_character_count"] == body_start
    assert accounting["unaccounted_character_count"] == 0
    assert len(issues) == 1 and issues[0].issue_type == "source_text_recovered"
    assert "dev_source_accounting stage=completed" in caplog.text
    assert "actualUnaccountedCharacters=0" in caplog.text
    assert "recoveredRangeCount=1" in caplog.text


def test_unmapped_table_cell_text_is_recovered_as_searchable_source():
    block = ParsedBlock("cell", "Longsword 15 gp", SourceSpan(1, 0, 0, 15, table_id="t1", table_cell="r0:c0"))
    cell = TableCell("cell", 0, 0, "Longsword 15 gp (wrong)", BoundingBox(0, 0, 30, 10), source_block_ids=("cell",))
    table = TableStructure("t1", BoundingBox(0, 0, 30, 10), (TableRow(0, (cell,), True),), (), (cell,))
    page = ParsedPage(1, (block,), block.source_text, tables=(table,))
    section = SectionNode("s1", "Equipment", 1, ContentType.RULE,
                          block_ids=(block.block_id,),
                          source_segments=(SourceSegment(block.source_text, block.source_span),))
    tree = DocumentTree("doc", SectionNode("root", "doc", 0, ContentType.NARRATIVE, children=(section,)))
    document = ParsedDocument("doc", "rules.pdf", block.source_text, (page,))

    chunks, accounting, _ = _recover_uncovered_text(document, tree, (), ChunkPolicy())

    assert len(chunks) == 1
    assert chunks[0].source_text == block.source_text
    assert chunks[0].content_type is ContentType.RULE
    assert chunks[0].source_spans == (block.source_span,)
    assert accounting["recovered_character_count"] == len(block.source_text)
    assert accounting["unaccounted_character_count"] == 0


def test_unowned_recovery_remains_searchable_without_inventing_a_section_path():
    text = "Text without a confirmed section."
    block = ParsedBlock("p1-b0", text, SourceSpan(1, 0, 0, len(text)))
    document = ParsedDocument("doc", "rules.pdf", text, (ParsedPage(1, (block,), text),))
    tree = DocumentTree("doc", SectionNode("root", "doc", 0, ContentType.NARRATIVE))

    chunks, _, _ = _recover_uncovered_text(document, tree, (), ChunkPolicy())

    assert len(chunks) == 1
    assert chunks[0].source_text == text
    assert chunks[0].content_type is ContentType.UNKNOWN
    assert chunks[0].section_path == ()


def test_separator_only_unowned_text_is_reported_as_intentionally_excluded():
    block = ParsedBlock("p1-b0", "7", SourceSpan(1, 0, 0, 1))
    document = ParsedDocument("doc", "rules.pdf", "7", (ParsedPage(1, (block,), "7"),))
    tree = DocumentTree("doc", SectionNode("root", "doc", 0, ContentType.NARRATIVE))

    chunks, accounting, issues = _recover_uncovered_text(document, tree, (), ChunkPolicy())

    assert chunks == ()
    assert issues == ()
    assert accounting["intentionally_excluded_character_count"] == 1
    assert accounting["intentionally_excluded_block_count"] == 1
    assert accounting["unaccounted_character_count"] == 0
