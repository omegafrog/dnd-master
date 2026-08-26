from preprocessing_agent.chunking import ChunkPolicy, ChunkSplitter
from preprocessing_agent.domain import ChunkCandidate, ContentType, SourceSegment, SourceSpan


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
    policy = ChunkPolicy(target_tokens=3, max_tokens=5, min_tokens=1, overlap_tokens=1)
    text = "| d6 | result |\n| 1 | long result |\n| 2 | another |"
    pieces = ChunkSplitter(policy).split(candidate(ContentType.TABLE, text))
    assert [piece.source_text for piece in pieces] == [text]


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
