from preprocessing_agent.domain import Chunk, ContentType, SourceSpan
from preprocessing_agent.postprocessing import postprocess_chunks


def make_chunk(text: str, key: str = "rules.attack", content_type: ContentType = ContentType.RULE) -> Chunk:
    return Chunk("chk-" + key, key, content_type, text, text, len(text.split()), (SourceSpan(1),), ("rules",))


def test_postprocessing_normalizes_embedding_only_and_keeps_provenance_exact():
    source = "A charac-\nter cre-\nates.\n# Heading\nbody"
    chunk = make_chunk(source)

    processed = postprocess_chunks((chunk,))[0]

    assert processed.source_text == source
    assert processed.source_spans == chunk.source_spans
    assert "character creates" in processed.embedding_text
    assert "# Heading\n\nbody" in processed.embedding_text


def test_repeated_noise_is_removed_only_when_repeated():
    chunks = (
        make_chunk("Page title\nBody one.", "a"),
        make_chunk("Page title\nBody two.", "b"),
    )
    processed = postprocess_chunks(chunks)

    assert all(item.source_text.startswith("Page title") for item in processed)
    assert all(not item.embedding_text.startswith("Page title") for item in processed)
