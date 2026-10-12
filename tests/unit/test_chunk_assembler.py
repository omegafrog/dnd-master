from preprocessing_agent.chunking import ChunkAssembler, ChunkPolicy, ChunkSplitter
from preprocessing_agent.domain import ChunkCandidate, ContentType, SourceSpan


def test_assembly_has_source_embedding_provenance_and_stable_id():
    candidate = ChunkCandidate("c", "combat.attack", ContentType.RULE, "Make an attack.", (SourceSpan(1),), ("combat", "attack"))
    assembler = ChunkAssembler(ChunkSplitter(ChunkPolicy()))
    first = assembler.assemble([candidate])[0]
    second = assembler.assemble([candidate])[0]
    assert first == second
    assert first.embedding_text == first.source_text
    assert first.token_count == 3
    assert first.source_spans == candidate.source_spans
    assert first.chunk_id.startswith("chk_")


def test_identical_text_at_distinct_canonical_locations_has_distinct_ids():
    candidates = (
        ChunkCandidate("first", "rules.p0001.b0001", ContentType.RULE, "Repeated rule.",
                       (SourceSpan(1, 1, 0, 14),), ("rules",)),
        ChunkCandidate("second", "rules.p0002.b0001", ContentType.RULE, "Repeated rule.",
                       (SourceSpan(2, 1, 0, 14),), ("rules",)),
    )

    chunks = ChunkAssembler(ChunkSplitter(ChunkPolicy())).assemble(candidates)

    assert chunks[0].source_text == chunks[1].source_text
    assert chunks[0].chunk_id != chunks[1].chunk_id
