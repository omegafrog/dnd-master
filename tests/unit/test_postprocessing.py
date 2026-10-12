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


def test_footer_artifact_is_removed_when_it_occurs_mid_chunk():
    source = (
        "The rule text before the page artifact. D&D Basic Rules (Version 1.0). "
        "Not for resale. Permission is granted to print and reproduce this document "
        "for personal use only. The rule text after the page artifact."
    )
    chunk = make_chunk(source)

    processed = postprocess_chunks((chunk,))[0]

    assert processed.source_text == source
    assert processed.source_spans == chunk.source_spans
    assert "D&D Basic Rules (Version 1.0). Not for resale" not in processed.embedding_text
    assert "Permission is granted to print and reproduce this document" not in processed.embedding_text
    assert "before the page artifact" in processed.embedding_text
    assert "after the page artifact" in processed.embedding_text


def test_actual_basic_rules_footer_with_photocopy_is_removed():
    source = (
        "The rule text before the page artifact. D&D Basic Rules (Version 1.0). "
        "Not for resale. Permission granted to print and photocopy this document "
        "for personal use only. The rule text after the page artifact."
    )
    chunk = make_chunk(source)

    processed = postprocess_chunks((chunk,))[0]

    assert processed.source_text == source
    assert "D&D Basic Rules (Version 1.0). Not for resale" not in processed.embedding_text
    assert "Permission granted to print and photocopy this document" not in processed.embedding_text
    assert "before the page artifact" in processed.embedding_text
    assert "after the page artifact" in processed.embedding_text


def test_postprocessing_preserves_table_rows_matching_repeated_page_boundaries(monkeypatch, caplog):
    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    caplog.set_level("INFO")
    table_source = "1 | +1 |\n2 | +2 |\n3 | +3 |"
    table = make_chunk(table_source, "ability-table", ContentType.TABLE)
    boundary_chunks = (
        make_chunk("1 | +1 |\nRule A", "rule-a"),
        make_chunk("Rule B\n1 | +1 |", "rule-b"),
    )

    processed = postprocess_chunks((*boundary_chunks, table))[-1]

    assert processed.source_text == table_source
    assert processed.embedding_text == table_source
    assert processed.token_count == len(table_source.split())
    assert processed.token_count > 0
    assert processed.source_spans == table.source_spans
    assert "dev_chunk_postprocess stage=preserve_table_embedding chunkId=chk-ability-table sourceCharacters=26 embeddingCharacters=26 tokenCount=12 repeatedBoundaryCleanup=skipped outcome=success" in caplog.text
