import pytest

from preprocessing_agent.domain import (
    Chunk,
    ContentType,
    DocumentTree,
    ParsedBlock,
    ParsedDocument,
    ParsedPage,
    SectionNode,
    SourceSegment,
    SourceSpan,
)
from preprocessing_agent.validation import ValidationPolicy, validate_chunks


def chunk(key="rules.attack", text="Make an attack.", span=None, tokens=3, section=("rules", "attack"), content_type=ContentType.RULE):
    return Chunk("chk-" + key, key, content_type, text, text, tokens,
                 (span or SourceSpan(1, 0, 0, len(text)),), section)


def test_validator_reports_the_contract_rules_deterministically():
    chunks = (
        chunk("orphan", "Heading", tokens=1, section=()),
        chunk("overflow", "too many", tokens=501),
        chunk("duplicate", "same", tokens=1),
        chunk("duplicate-2", "same", tokens=1),
        chunk("bad-span", "text", span=SourceSpan(9, 0, 0, 4)),
        chunk("table", "| a |\n| b |", section=("rules",), content_type=ContentType.TABLE),
    )
    result = validate_chunks(chunks, page_count=1, block_count=1,
                             policy=ValidationPolicy(min_tokens=2, max_tokens=500))
    assert not result.valid
    types = [issue.issue_type for issue in result.issues]
    assert types[:2] == ["too_small_chunk", "broken_sentence"]
    assert {"orphan_heading", "max_token_overflow", "duplicate",
            "invalid_source_span"} <= set(types)


def test_empty_and_broken_sentence_are_reported():
    result = validate_chunks((chunk("bad", "", tokens=0, section=("rules",)),),
                             page_count=1, block_count=1)
    types = {issue.issue_type for issue in result.issues}
    assert {"empty_chunk", "too_small_chunk", "broken_sentence"} <= types


def test_missing_section_path_is_not_an_error_when_source_belongs_to_unresolved_heading(monkeypatch, caplog):
    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    caplog.set_level("INFO")
    text = "Take cover."
    span = SourceSpan(1, 2, 0, len(text))
    unresolved = SectionNode("s1", "Cover", 1, ContentType.RULE,
                             source_spans=(span,), block_ids=("p1-b2",),
                             source_segments=(SourceSegment(text, span),), hierarchy_resolved=False)
    tree = DocumentTree("doc", SectionNode("root", "doc", 0, ContentType.NARRATIVE,
                                             children=(unresolved,)))

    result = validate_chunks((chunk("unresolved", text, span=span, tokens=2, section=()),),
                             tree=tree, policy=ValidationPolicy(min_tokens=1))

    assert result.valid
    assert not any(issue.issue_type in {"orphan_heading", "missing_section_path"} for issue in result.issues)
    assert "unresolvedHierarchyFullyCoveredChunkCount=1 missingPathIssueCount=0" in caplog.text


def test_missing_section_path_still_fails_without_matching_unresolved_heading():
    text = "Take cover."
    span = SourceSpan(1, 2, 0, len(text))
    tree = DocumentTree("doc", SectionNode("root", "doc", 0, ContentType.NARRATIVE))

    result = validate_chunks((chunk("unresolved", text, span=span, tokens=2, section=()),),
                             tree=tree, policy=ValidationPolicy(min_tokens=1))

    assert not result.valid
    assert any(issue.issue_type == "orphan_heading" for issue in result.issues)


def test_partial_overlap_with_unresolved_heading_does_not_hide_missing_path():
    text = "Take cover now."
    span = SourceSpan(1, 2, 0, len(text))
    unresolved_span = SourceSpan(1, 2, 0, 5)
    unresolved = SectionNode("s1", "Cover", 1, ContentType.RULE,
                             source_spans=(unresolved_span,), block_ids=("p1-b2",),
                             source_segments=(SourceSegment("Take ", unresolved_span),),
                             hierarchy_resolved=False)
    tree = DocumentTree("doc", SectionNode("root", "doc", 0, ContentType.NARRATIVE,
                                             children=(unresolved,)))

    result = validate_chunks((chunk("partial", text, span=span, tokens=3, section=()),),
                             tree=tree, policy=ValidationPolicy(min_tokens=1))

    assert not result.valid
    assert any(issue.issue_type in {"orphan_heading", "missing_section_path"} for issue in result.issues)


def test_missing_path_is_allowed_when_adjacent_unresolved_ranges_cover_every_source_character():
    text = "Take cover."
    span = SourceSpan(1, 2, 0, len(text))
    first = SourceSpan(1, 2, 0, 5)
    second = SourceSpan(1, 2, 5, len(text))
    unresolved = SectionNode("s1", "Cover", 1, ContentType.RULE,
                             source_segments=(SourceSegment("Take ", first),
                                              SourceSegment("cover.", second)),
                             hierarchy_resolved=False)
    tree = DocumentTree("doc", SectionNode("root", "doc", 0, ContentType.NARRATIVE,
                                             children=(unresolved,)))

    result = validate_chunks((chunk("fully-covered", text, span=span, tokens=2, section=()),),
                             tree=tree, policy=ValidationPolicy(min_tokens=1))

    assert not any(issue.issue_type in {"orphan_heading", "missing_section_path"} for issue in result.issues)


def test_missing_path_is_not_allowed_when_one_of_multiple_source_spans_is_uncovered():
    first_text, second_text = "Take ", "cover."
    spans = (SourceSpan(1, 2, 0, len(first_text)), SourceSpan(1, 3, 0, len(second_text)))
    unresolved = SectionNode("s1", "Cover", 1, ContentType.RULE,
                             source_segments=(SourceSegment(first_text, spans[0]),),
                             hierarchy_resolved=False)
    tree = DocumentTree("doc", SectionNode("root", "doc", 0, ContentType.NARRATIVE,
                                             children=(unresolved,)))
    item = Chunk("chk-mixed", "rules.mixed", ContentType.RULE,
                 first_text + second_text, first_text + second_text, 2, spans, ())

    result = validate_chunks((item,), tree=tree, policy=ValidationPolicy(min_tokens=1))

    assert not result.valid
    assert any(issue.issue_type in {"orphan_heading", "missing_section_path"} for issue in result.issues)


def _parsed_document() -> ParsedDocument:
    block_text = "x" * 52
    page_text = "p\n" + block_text
    block = ParsedBlock("p1-b1", block_text, SourceSpan(1, 1, 2, 54))
    return ParsedDocument(
        "doc",
        "fixture.pdf",
        page_text,
        (ParsedPage(1, (ParsedBlock("p1-b0", "p", SourceSpan(1, 0, 0, 1)), block), page_text),),
    )


def test_validator_accepts_page_relative_offset_beyond_block_text_length():
    document = _parsed_document()
    result = validate_chunks(
        (chunk("page-relative", "x", span=SourceSpan(1, 1, 2, 54), section=("rules",)),),
        document=document,
    )

    assert not any(issue.issue_type == "invalid_source_span" for issue in result.issues)


@pytest.mark.parametrize(
    "span",
    (
        SourceSpan(2, 0, 0, 1),
        SourceSpan(1, 2, 0, 1),
        SourceSpan(1, 1, 0, 55),
    ),
    ids=("missing-page", "page-local-block-overflow", "page-offset-overflow"),
)
def test_validator_rejects_invalid_page_block_and_page_offsets(span):
    result = validate_chunks(
        (chunk("invalid-span", "x", span=span, section=("rules",)),),
        document=_parsed_document(),
    )

    assert any(issue.issue_type == "invalid_source_span" for issue in result.issues)


def test_validator_rejects_duplicate_chunk_and_canonical_ids():
    first = chunk("same", "First.")
    duplicate_chunk_id = Chunk(first.chunk_id, "rules.other", ContentType.RULE, "Second.", "Second.", 1,
                               first.source_spans, first.section_path)
    duplicate_key = Chunk("chk-other", first.canonical_key, ContentType.RULE, "Third.", "Third.", 1,
                          first.source_spans, first.section_path)

    result = validate_chunks((first, duplicate_chunk_id, duplicate_key), page_count=1, block_count=1)

    assert {issue.issue_type for issue in result.issues} >= {"duplicate_chunk_id", "duplicate_canonical_key"}


def test_validator_only_reports_split_table_for_a_real_multi_chunk_table_candidate():
    first = Chunk("chk-table-1", "rules.table.part-001", ContentType.TABLE, "| a |", "| a |", 3,
                  (SourceSpan(1),), ("rules",), "rules.table")
    second = Chunk("chk-table-2", "rules.table.part-002", ContentType.TABLE, "| b |", "| b |", 3,
                   (SourceSpan(1),), ("rules",), "rules.table")
    single = chunk("single-table", "| a |\n| b |", section=("rules",), content_type=ContentType.TABLE)

    result = validate_chunks((first, second, single), policy=ValidationPolicy(min_tokens=1))

    assert [issue.path for issue in result.issues if issue.issue_type == "split_table"] == ["chk-table-1", "chk-table-2"]


def test_warnings_do_not_make_deterministic_validation_invalid():
    result = validate_chunks((chunk("short", "Fragment", tokens=1),), policy=ValidationPolicy(min_tokens=100))

    assert result.valid
    assert {issue.severity for issue in result.issues} == {"warning"}
