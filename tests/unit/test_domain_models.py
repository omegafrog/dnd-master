import pytest

from preprocessing_agent.domain import (
    Chunk,
    ChunkCandidate,
    ContentType,
    DocumentTree,
    ParsedBlock,
    ParsedDocument,
    ParsedPage,
    SectionNode,
    SourceSpan,
    ValidationIssue,
    ValidationResult,
    from_json,
    to_json,
)


def test_content_type_contract_contains_initial_values_and_unknown() -> None:
    assert {item.value for item in ContentType} == {
        "narrative", "rule", "table", "class_feature", "race_trait", "spell",
        "monster_stat_block", "magic_item", "condition", "equipment", "background", "unknown",
    }


def test_source_span_rejects_invalid_ranges() -> None:
    with pytest.raises(ValueError):
        SourceSpan(page_number=0)
    with pytest.raises(ValueError):
        SourceSpan(page_number=1, char_start=4, char_end=2)


def test_models_are_immutable_and_preserve_source_text() -> None:
    block = ParsedBlock("b1", "original rule text", SourceSpan(1, 0, 0, 18))
    with pytest.raises((AttributeError, TypeError)):
        block.source_text = "changed"
    assert block.source_text == "original rule text"


def test_nested_model_round_trip_preserves_enum_and_spans() -> None:
    chunk = Chunk(
        "chk_abc", "ch01.rule", ContentType.RULE, "original", "original", 1,
        (SourceSpan(1, 0, 0, 8),), ("Chapter 1",), None,
    )
    restored = from_json(Chunk, to_json(chunk))
    assert restored == chunk
    assert restored.content_type is ContentType.RULE


def test_all_contract_models_round_trip() -> None:
    span = SourceSpan(1, 0, 0, 8)
    block = ParsedBlock("b1", "original", span, (0.0, 1.0, 2.0, 3.0), 10.0, "bold")
    page = ParsedPage(1, (block,), "original")
    document = ParsedDocument("doc", "rules.pdf", "original", (page,), {"edition": "2018"})
    node = SectionNode("n1", "Rules", 0, ContentType.RULE, (span,), ("b1",))
    tree = DocumentTree("doc", node)
    candidate = ChunkCandidate("cand", "rules", ContentType.RULE, "original", (span,), ("Rules",))
    issue = ValidationIssue("duplicate", "duplicate", "warning", "$.chunks[0]", span)
    result = ValidationResult(False, (issue,), ("chk",))

    for model_type, model in (
        (ParsedDocument, document), (DocumentTree, tree), (ChunkCandidate, candidate),
        (ValidationResult, result),
    ):
        assert from_json(model_type, to_json(model)) == model


def test_validation_issue_rejects_unknown_severity() -> None:
    with pytest.raises(ValueError):
        ValidationIssue("bad", "bad", severity="fatal")
