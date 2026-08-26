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
