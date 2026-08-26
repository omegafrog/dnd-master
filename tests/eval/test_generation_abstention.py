import json

import pytest

from preprocessing_agent.eval.gold import GoldCase, RequiredEvidence
from preprocessing_agent.eval.reranker import GenerationHandoff
from preprocessing_agent.eval.generation import (
    CitedAnswer,
    GenerationInputError,
    InjectedGenerator,
    evaluate_generation,
    validate_citations,
)


def _handoff(*chunk_ids: str) -> GenerationHandoff:
    return GenerationHandoff(
        query="What is the rule?",
        context=tuple({"chunk_id": chunk_id, "text": f"text for {chunk_id}",
                       "source_citation": f"rule.{chunk_id}", "relation": "retrieved"}
                      for chunk_id in chunk_ids),
        citations=tuple({"chunk_id": chunk_id, "source_citation": f"rule.{chunk_id}"}
                        for chunk_id in chunk_ids),
        retrieval_gold_ids=tuple(chunk_ids),
        adapter_metadata={"mode": "offline"},
    )


def test_citations_must_reference_context_and_preserve_provenance():
    handoff = _handoff("c1")
    answer = CitedAnswer("supported", ("c1",), abstained=False)

    assert validate_citations(answer, handoff).valid
    assert not validate_citations(CitedAnswer("bad", ("missing",)), handoff).valid


def test_unanswerable_and_low_evidence_cases_abstain_without_calling_generator():
    calls = []
    generator = InjectedGenerator(lambda query, context: calls.append(query) or CitedAnswer("answer", ("c1",)))
    cases = (
        GoldCase("unknown", "outside", answerable=False),
        GoldCase("weak", "What is the rule?", gold_chunk_ids=("c1",), answerable=True,
                 evidence_groups=(RequiredEvidence("required", ("c1", "c2")),)),
    )

    report = evaluate_generation(
        {"unknown": _handoff("c1"), "weak": _handoff("c1")}, cases, generator,
        min_evidence_ratio=1.0,
    )

    assert calls == []
    assert [item["abstention_reason"] for item in report.details] == [
        "UNANSWERABLE", "INSUFFICIENT_EVIDENCE"
    ]
    assert report.metrics["abstention_accuracy"] == 1.0


def test_generation_report_records_metrics_and_separate_failure_artifacts(tmp_path):
    cases = (GoldCase("q1", "What is the rule?", gold_chunk_ids=("c1",), answerable=True),)
    report = evaluate_generation(
        {"q1": _handoff("c1")}, cases,
        InjectedGenerator(lambda query, context: CitedAnswer("supported", ("c1",))),
        output=tmp_path,
        judges={"correctness": lambda case, answer: True,
                "groundedness": lambda case, answer, context: True},
    )

    assert report.metrics["citation_correctness"] == 1.0
    assert report.metrics["faithfulness"] == 1.0
    assert report.metrics["context_utilization"] == 1.0
    summary = json.loads((tmp_path / "generation_summary.json").read_text())
    assert summary["metrics"] == report.metrics
    assert (tmp_path / "generation_details.jsonl").is_file()
    assert (tmp_path / "generation_failures.jsonl").is_file()


def test_invalid_generator_output_is_rejected():
    with pytest.raises(GenerationInputError):
        InjectedGenerator(lambda query, context: {"answer": "missing citations"}).generate("q", ())
