from preprocessing_agent.chunking import ChunkPolicy, ChunkSplitter
from preprocessing_agent.domain import ChunkCandidate, ContentType, SourceSpan


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
