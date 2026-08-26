import json

import pytest

from preprocessing_agent.domain import Chunk, ContentType
from preprocessing_agent.eval.gold import (
    GoldCase,
    GoldValidationError,
    validate_gold_cases,
    write_gold_validation,
)


def chunk(chunk_id: str) -> Chunk:
    return Chunk(chunk_id, f"key.{chunk_id}", ContentType.RULE, "text", "text", 1, ())


def test_validation_accepts_explicit_gold_ids_and_evidence_groups(tmp_path):
    cases = (
        GoldCase.from_dict({
            "case_id": "q1", "question": "What?", "answerable": True,
            "gold_chunk_ids": ["c1"],
            "evidence_groups": [{"group_id": "answer", "chunk_ids": ["c1"]}],
        }),
        GoldCase.from_dict({"case_id": "q2", "question": "Unknown?", "answerable": False}),
    )

    result = validate_gold_cases(cases, (chunk("c1"), chunk("c2")), expected_case_ids=("q1", "q2"))

    assert result.valid
    assert result.metrics["answerable_cases"] == 1
    output = write_gold_validation(result, tmp_path / "gold_validation.json")
    assert json.loads(output.read_text())["valid"] is True


@pytest.mark.parametrize(
    "case, issue_type",
    [
        ({"case_id": "q1", "question": "?", "answerable": True}, "ANSWERABLE_WITHOUT_GOLD"),
        ({"case_id": "q1", "question": "?", "answerable": False, "gold_chunk_ids": ["c1"]}, "UNANSWERABLE_WITH_GOLD"),
        ({"case_id": "q1", "question": "?", "answerable": True, "gold_chunk_ids": ["missing"]}, "UNKNOWN_GOLD_CHUNK_ID"),
        ({"case_id": "q1", "question": "?", "answerable": True, "gold_chunk_ids": ["c1", "c1"]}, "DUPLICATE_GOLD_CHUNK_ID"),
    ],
)
def test_validation_reports_gold_contract_violations(case, issue_type):
    result = validate_gold_cases((GoldCase.from_dict(case),), (chunk("c1"),), expected_case_ids=("q1",))
    assert not result.valid
    assert issue_type in {issue["type"] for issue in result.issues}


def test_validation_reports_duplicate_and_missing_cases():
    cases = (GoldCase.from_dict({"case_id": "q1", "question": "?", "answerable": False}),
             GoldCase.from_dict({"case_id": "q1", "question": "duplicate", "answerable": False}))
    result = validate_gold_cases(cases, (), expected_case_ids=("q1", "q2"))
    assert {issue["type"] for issue in result.issues} >= {"DUPLICATE_CASE_ID", "MISSING_CASE_ID"}


def test_invalid_gold_blocks_retrieval(tmp_path):
    from preprocessing_agent.eval.report import evaluate_run
    from preprocessing_agent.eval.preprocessing import ExportedRun
    from preprocessing_agent.domain import DocumentTree, SectionNode

    run = ExportedRun(tmp_path, (chunk("c1"),), DocumentTree("doc", SectionNode("root", "Root", 0, ContentType.RULE)), {}, None, ())
    (tmp_path / "gold.jsonl").write_text(json.dumps({"case_id": "q1", "question": "?", "answerable": True}) + "\n")
    retriever = lambda query, limit=10: pytest.fail("retriever must not run")
    report, _ = evaluate_run(run, eval_path=tmp_path / "gold.jsonl", retriever=retriever)
    assert report["retrieval"]["blocked_by_gold_validation"] is True
