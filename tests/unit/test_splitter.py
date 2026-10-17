from preprocessing_agent.chunking import ChunkPolicy, ChunkSplitter
from preprocessing_agent.domain import ChunkCandidate, ContentType, SourceSegment, SourceSpan
from preprocessing_agent.utils.tokens import count_tokens


def candidate(kind: ContentType, text: str) -> ChunkCandidate:
    return ChunkCandidate("c", "spell.fireball", kind, text, (SourceSpan(1),))


def test_atomic_overflow_uses_parent_child_identity():
    policy = ChunkPolicy(target_tokens=3, max_tokens=5, min_tokens=1, overlap_tokens=1)
    pieces = ChunkSplitter(policy).split(candidate(ContentType.SPELL, "one two three four five six seven"))
    assert len(pieces) == 2
    assert all(piece.parent_key == "spell.fireball" for piece in pieces)
    assert pieces[0].canonical_key == "spell.fireball.part-001"
    assert all(len(piece.source_text.split()) <= 5 for piece in pieces)


def test_table_is_preserved_even_when_over_max():
    policy = ChunkPolicy(target_tokens=3, max_tokens=20, max_characters=25, min_tokens=1, overlap_tokens=1)
    text = "| d6 | result |\n| 1 | long result |\n| 2 | another |"
    pieces = ChunkSplitter(policy).split(candidate(ContentType.TABLE, text))
    assert [line for piece in pieces for line in piece.source_text.splitlines()] == text.splitlines()
    assert all(len(piece.source_text) <= policy.max_characters for piece in pieces)


def test_prose_uses_smaller_limit_while_table_keeps_existing_limit(monkeypatch, caplog):
    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    caplog.set_level("INFO")
    policy = ChunkPolicy(max_tokens=500, prose_max_tokens=180, min_tokens=1, overlap_tokens=30)
    prose = candidate(ContentType.RULE, " ".join(f"Sentence {i} contains useful rule details." for i in range(100)))
    table_text = "\n".join(f"Row {i} contains a complete table entry." for i in range(60))
    table = candidate(ContentType.TABLE, table_text)

    prose_pieces = ChunkSplitter(policy).split(prose)
    table_pieces = ChunkSplitter(policy).split(table)

    assert len(prose_pieces) > 1
    assert max(count_tokens(piece.source_text) for piece in prose_pieces) <= 180
    joined_pieces = "\n".join(piece.source_text for piece in prose_pieces)
    assert all(f"Sentence {i} contains useful rule details." in joined_pieces for i in range(100))
    assert "dev_chunk_splitter stage=start" in caplog.text
    assert "expectedMaxTokens=180" in caplog.text
    assert "dev_chunk_splitter stage=complete" in caplog.text
    assert "outcome=split" in caplog.text
    assert len(table_pieces) == 1
    assert table_pieces[0].source_text == table_text


def test_atomic_chunk_splits_by_characters_below_token_limit():
    policy = ChunkPolicy(target_tokens=350, max_tokens=500, max_characters=6000, min_tokens=1, overlap_tokens=30)
    text = "first " + "a" * 3500 + " second " + "b" * 3500
    assert count_tokens(text) <= 500
    assert len(text) > 6000

    pieces = ChunkSplitter(policy).split(candidate(ContentType.MAGIC_ITEM, text))

    assert len(pieces) == 2
    assert all(len(piece.source_text) <= 6000 for piece in pieces)
    assert all(piece.parent_key == "spell.fireball" for piece in pieces)


def test_oversized_table_splits_only_between_rows():
    policy = ChunkPolicy(target_tokens=350, max_tokens=500, max_characters=6000, min_tokens=1, overlap_tokens=30)
    rows = [f"row-{index} | {'x' * 900}" for index in range(8)]
    text = "\n".join(rows)

    pieces = ChunkSplitter(policy).split(candidate(ContentType.TABLE, text))

    assert len(pieces) > 1
    assert all(piece.source_text.splitlines() == [row for row in rows if row in piece.source_text] for piece in pieces)
    assert "\n".join(row for piece in pieces for row in piece.source_text.splitlines()) == text


def test_long_character_sections_split_even_when_whitespace_token_count_is_low(monkeypatch, caplog):
    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    caplog.set_level("INFO")
    text = "first............. entry\nsecond............. entry\nthird............. entry"
    policy = ChunkPolicy(target_tokens=10, max_tokens=100, max_characters=32, min_tokens=1, overlap_tokens=0)

    pieces = ChunkSplitter(policy).split(candidate(ContentType.RULE, text))

    assert len(pieces) > 1
    assert max(len(piece.source_text) for piece in pieces) <= policy.max_characters
    assert "dev_chunk_splitter stage=complete canonicalKey=spell.fireball" in caplog.text
    assert "expectedMaxCharacters=32" in caplog.text


def test_semantic_split_keeps_only_page_local_spans_covering_piece():
    text = "Header\n\nMedium Armor\n\nUnrelated section"
    spans = (
        SourceSpan(47, block_index=0, char_start=100, char_end=106),
        SourceSpan(47, block_index=1, char_start=107, char_end=119),
        SourceSpan(47, block_index=2, char_start=120, char_end=137),
    )
    candidate_value = ChunkCandidate(
        "c", "equipment.armor", ContentType.RULE, text, spans,
        source_segments=tuple(
            SourceSegment(part, span)
            for part, span in zip(text.split("\n\n"), spans)
        ),
    )

    pieces = ChunkSplitter(ChunkPolicy(target_tokens=2, max_tokens=2, min_tokens=1, overlap_tokens=0)).split(candidate_value)

    medium = next(piece for piece in pieces if "Medium Armor" in piece.source_text)
    assert medium.source_text == "Medium Armor"
    assert medium.source_spans == (SourceSpan(47, 1, 107, 119),)
    assert all(span.block_index == 1 for span in medium.source_spans)


def test_semantic_split_retains_source_geometry_and_table_cell_identity():
    text = "Name\n\nValue"
    span = SourceSpan(2, 4, 10, 14, bbox=(1, 2, 3, 4), table_id="p2-table-0", table_cell="r1:c0")
    candidate_value = ChunkCandidate(
        "c", "rule.table", ContentType.RULE, text, (span,),
        source_segments=(SourceSegment("Name", span), SourceSegment("Value", span)),
    )

    pieces = ChunkSplitter(ChunkPolicy(target_tokens=1, max_tokens=1, min_tokens=1, overlap_tokens=0)).split(candidate_value)

    assert pieces[0].source_spans[0].bbox == (1, 2, 3, 4)
    assert pieces[0].source_spans[0].table_id == "p2-table-0"
    assert pieces[0].source_spans[0].table_cell == "r1:c0"


def test_atomic_split_does_not_inherit_unrelated_source_spans():
    text = "one two three\n\nfour five six"
    spans = (SourceSpan(47, 0, 10, 23), SourceSpan(47, 1, 24, 37))
    candidate_value = ChunkCandidate(
        "c", "spell.fireball", ContentType.SPELL, text, spans,
        source_segments=(SourceSegment("one two three", spans[0]), SourceSegment("four five six", spans[1])),
    )

    pieces = ChunkSplitter(ChunkPolicy(target_tokens=3, max_tokens=3, min_tokens=1, overlap_tokens=0)).split(candidate_value)

    assert pieces[0].source_spans == (SourceSpan(47, 0, 10, 23),)
    assert pieces[1].source_spans == (SourceSpan(47, 1, 24, 37),)


def test_split_without_precise_segments_reports_trace_error_safely():
    value = ChunkCandidate(
        "c", "rule", ContentType.RULE, "one two three four", (SourceSpan(1), SourceSpan(1, 1)),
    )

    pieces = ChunkSplitter(ChunkPolicy(target_tokens=2, max_tokens=2, min_tokens=1, overlap_tokens=0)).split(value)

    assert pieces
    assert all(piece.source_spans == () for piece in pieces)
    assert all(piece.provenance_error == "source segment mapping unavailable" for piece in pieces)
