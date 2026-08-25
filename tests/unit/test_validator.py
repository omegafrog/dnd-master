import pytest

from preprocessing_agent.domain import (
    Chunk,
    ContentType,
    ParsedBlock,
    ParsedDocument,
    ParsedPage,
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
            "invalid_source_span", "split_table"} <= set(types)


def test_empty_and_broken_sentence_are_reported():
    result = validate_chunks((chunk("bad", "", tokens=0, section=("rules",)),),
                             page_count=1, block_count=1)
    types = {issue.issue_type for issue in result.issues}
    assert {"empty_chunk", "too_small_chunk", "broken_sentence"} <= types


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
