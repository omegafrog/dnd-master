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


def test_repeated_footer_and_page_number_are_removed_from_embedding_only():
    footer = "D&D Basic Rules"
    chunks = (
        make_chunk(f"Body one.\n{footer} | 7", "a"),
        make_chunk(f"Body two.\n{footer} | 8", "b"),
    )

    processed = postprocess_chunks(chunks)

    assert [item.source_text for item in processed] == [item.source_text for item in chunks]
    assert all(footer in item.source_text for item in processed)
    assert all(footer not in item.embedding_text for item in processed)
    assert all(item.source_spans == chunks[index].source_spans for index, item in enumerate(processed))
