import json

from preprocessing_agent.domain import (
    Chunk,
    ContentType,
    DocumentTree,
    ParsedBlock,
    ParsedDocument,
    ParsedPage,
    SourceSpan,
    ValidationIssue,
    ValidationResult,
    SectionNode,
    schema_path,
    to_dict,
    validate_json,
)


def sample_models():
    span = SourceSpan(1, 0, 0, 18)
    block = ParsedBlock("b1", "original rule text", span)
    yield "parsed_document.schema.json", ParsedDocument("doc-1", "rules.pdf", "original rule text", (ParsedPage(1, (block,), "original rule text"),))
    yield "chunk.schema.json", Chunk("chk_abc", "ch01.rule", ContentType.RULE, "original", "original", 1, (span,))
    yield "validation.schema.json", ValidationResult(False, (ValidationIssue("empty", "empty source", "warning", "$.chunks[0]", span),), ("chk_abc",))
    yield "document_tree.schema.json", DocumentTree("doc-1", SectionNode("root", "Rules", 0, ContentType.RULE, (span,), ("b1",)))


def test_model_samples_validate_against_public_json_schemas() -> None:
    for filename, model in sample_models():
        schema = json.loads(schema_path(filename).read_text(encoding="utf-8"))
        validate_json(to_dict(model), schema)


def test_schema_rejects_unknown_content_type() -> None:
    schema = json.loads(schema_path("chunk.schema.json").read_text(encoding="utf-8"))
    invalid = {"chunk_id": "chk", "canonical_key": "key", "content_type": "unsupported"}
    try:
        validate_json(invalid, schema)
    except ValueError as exc:
        assert "enum" in str(exc)
    else:
        raise AssertionError("schema should reject unsupported content type")
