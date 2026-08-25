from preprocessing_agent.domain import Chunk, ContentType, SourceSpan, ValidationIssue
from preprocessing_agent.validation import RepairEngine, RepairOperation


def make(key, text, content_type=ContentType.RULE):
    return Chunk("chk-" + key, key, content_type, text, text, len(text.split()),
                 (SourceSpan(1, 0, 0, len(text)),), ("rules",))


def test_repair_engine_applies_only_allowlisted_deterministic_operations():
    chunks = (make("a", "first"), make("b", "second"))
    repaired = RepairEngine().apply(chunks, [
        (ValidationIssue("broken_sentence", "join", path="chk-a"), RepairOperation.MERGE_NEXT),
    ])
    assert len(repaired.chunks) == 1
    assert repaired.chunks[0].source_text == "first second"


def test_table_repair_is_manual_review():
    result = RepairEngine().apply((make("a", "| a |"),), [
        (ValidationIssue("split_table", "review", path="chk-a"), RepairOperation.SPLIT),
    ])
    assert result.manual_review
