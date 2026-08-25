from preprocessing_agent.domain import Chunk, ContentType, SourceSpan
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
