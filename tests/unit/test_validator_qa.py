from preprocessing_agent.domain import Chunk, ContentType, SourceSpan
from preprocessing_agent.validation import ValidationPolicy, validate_chunks


def make_chunk(key: str, text: str, content_type: ContentType = ContentType.RULE, tokens: int | None = None) -> Chunk:
    return Chunk("chk-" + key, key, content_type, text, text, tokens or len(text.split()), (SourceSpan(1),), ("rules",))


def test_validator_reports_garbage_and_malformed_keys_without_deleting_chunks():
    result = validate_chunks((make_chunk("bad key", "4433——"),), policy=ValidationPolicy(min_tokens=1))

    assert not result.valid
    assert {issue.issue_type for issue in result.issues} >= {"garbage_candidate", "malformed_canonical_key"}
    assert result.checked_chunk_ids == ("chk-bad key",)


def test_oversized_policy_is_strict_except_for_tables_and_stat_blocks():
    chunks = (
        make_chunk("rules.long", "word " * 501, tokens=501),
        make_chunk("rules.table", "word " * 501, ContentType.TABLE, 501),
        make_chunk("rules.stat", "word " * 501, ContentType.MONSTER_STAT_BLOCK, 501),
    )

    issues = validate_chunks(chunks, policy=ValidationPolicy(min_tokens=1)).issues

    assert [issue.path for issue in issues if issue.issue_type == "max_token_overflow"] == ["chk-rules.long"]
