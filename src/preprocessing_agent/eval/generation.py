"""Injected generation, citation validation, and deterministic abstention evaluation."""

from __future__ import annotations

from dataclasses import dataclass, field
import json
from pathlib import Path
from typing import Callable, Mapping, Protocol, Sequence

from .gold import GoldCase
from .reranker import GenerationHandoff


class GenerationInputError(ValueError):
    """A generation adapter or handoff violates the offline evaluation contract."""


@dataclass(frozen=True, slots=True)
class CitedAnswer:
    answer: str
    citations: tuple[str | Mapping[str, object], ...] = ()
    abstained: bool = False
    abstention_reason: str | None = None

    def __post_init__(self) -> None:
        if not isinstance(self.answer, str):
            raise GenerationInputError("answer must be a string")
        if self.abstained and self.citations:
            raise GenerationInputError("abstentions must not contain citations")

    @classmethod
    def from_value(cls, value: object) -> "CitedAnswer":
        if isinstance(value, cls):
            return value
        if not isinstance(value, Mapping):
            raise GenerationInputError("generator must return CitedAnswer or mapping")
        raw = value.get("citations", ()) or ()
        if not isinstance(raw, (list, tuple)):
            raise GenerationInputError("citations must be a sequence")
        citations = tuple(str(item) if isinstance(item, str) else dict(item) for item in raw)
        abstained = bool(value.get("abstained", False))
        answer = str(value.get("answer", ""))
        if not abstained and (not answer.strip() or not citations):
            raise GenerationInputError("non-abstained answers require text and citations")
        return cls(answer, citations, abstained,
                   str(value["abstention_reason"]) if value.get("abstention_reason") is not None else None)

    def to_dict(self) -> dict[str, object]:
        return {"answer": self.answer, "citations": [dict(item) if isinstance(item, Mapping) else item for item in self.citations],
                "abstained": self.abstained, "abstention_reason": self.abstention_reason}


class GenerationPort(Protocol):
    def generate(self, query: str, context: Sequence[Mapping[str, object]]) -> CitedAnswer: ...


GeneratorFunction = Callable[[str, Sequence[Mapping[str, object]]], CitedAnswer | Mapping[str, object]]


@dataclass(frozen=True, slots=True)
class InjectedGenerator:
    """Offline generation seam; no provider, network, or secret is involved."""

    generator: GeneratorFunction

    def generate(self, query: str, context: Sequence[Mapping[str, object]]) -> CitedAnswer:
        if not callable(self.generator):
            raise GenerationInputError("generator must be callable")
        return CitedAnswer.from_value(self.generator(query, context))


@dataclass(frozen=True, slots=True)
class CitationValidation:
    valid: bool
    valid_citations: tuple[str, ...] = ()
    issues: tuple[str, ...] = ()


def validate_citations(answer: CitedAnswer, handoff: GenerationHandoff) -> CitationValidation:
    context = {str(item.get("chunk_id")): item for item in handoff.context}
    handoff_citations = {str(item.get("chunk_id")): item for item in handoff.citations}
    valid: list[str] = []
    issues: list[str] = []
    for citation in answer.citations:
        if isinstance(citation, str):
            chunk_id, value = citation, {}
        elif isinstance(citation, Mapping):
            chunk_id, value = str(citation.get("chunk_id", "")), citation
        else:
            issues.append("INVALID_CITATION")
            continue
        if not chunk_id or chunk_id not in context:
            issues.append("UNKNOWN_CITATION_CHUNK")
            continue
        expected = {**dict(handoff_citations.get(chunk_id, {})), **dict(context[chunk_id])}
        for key in ("source_citation", "locator", "java_uuid"):
            if key in value and value[key] != expected.get(key):
                issues.append("CITATION_PROVENANCE_MISMATCH")
        valid.append(chunk_id)
    return CitationValidation(not issues, tuple(valid), tuple(issues))


@dataclass(frozen=True, slots=True)
class GenerationReport:
    metrics: Mapping[str, float | int]
    details: tuple[dict[str, object], ...]
    failures: tuple[dict[str, object], ...] = ()

    def to_dict(self) -> dict[str, object]:
        return {"metrics": dict(self.metrics), "details": list(self.details), "failures": list(self.failures)}


Judge = Callable[..., bool]


def _evidence_ids(case: GoldCase) -> frozenset[str]:
    groups = case.evidence_groups or case.required_evidence
    grouped = {chunk_id for group in groups for chunk_id in group.chunk_ids}
    return frozenset(grouped or case.gold_chunk_ids or case.gold_context_keys)


def _judge(judges: Mapping[str, Judge], name: str, *args: object) -> bool:
    judge = judges.get(name)
    if judge is None:
        return False
    try:
        return bool(judge(*args))
    except TypeError:
        return bool(judge(args[0], args[1]))


def evaluate_generation(
    handoffs: Mapping[str, GenerationHandoff], cases: Sequence[GoldCase], generator: GenerationPort,
    output: str | Path | None = None, *, min_evidence_ratio: float = 1.0,
    judges: Mapping[str, Judge] | None = None,
) -> GenerationReport:
    if not 0.0 <= min_evidence_ratio <= 1.0:
        raise GenerationInputError("min_evidence_ratio must be between 0 and 1")
    judges = judges or {}
    details: list[dict[str, object]] = []
    failures: list[dict[str, object]] = []
    counts = {"correctness": 0, "faithfulness": 0, "citation_correctness": 0,
              "context_utilization": 0, "abstention_accuracy": 0}
    for case in cases:
        handoff = handoffs.get(case.case_id)
        if handoff is None or not handoff.context or handoff.adapter_metadata.get("retrieval_failure"):
            detail = {"case_id": case.case_id, "abstained": True, "abstention_reason": "RETRIEVAL_FAILURE",
                      "answer": "", "citations": [], "generation_blocked": True}
            failures.append({"type": "RETRIEVAL_FAILURE", "case_id": case.case_id,
                             "details": {"message": "validated retrieval context is unavailable"}})
            details.append(detail)
            continue
        context_ids = {str(item.get("chunk_id")) for item in handoff.context}
        evidence = _evidence_ids(case)
        ratio = len(context_ids.intersection(evidence)) / len(evidence) if evidence else 0.0
        answerable = case.answerable if case.answerable is not None else bool(evidence)
        reason = "UNANSWERABLE" if not answerable else "INSUFFICIENT_EVIDENCE" if ratio < min_evidence_ratio else None
        answer = CitedAnswer("", (), True, reason) if reason else CitedAnswer.from_value(generator.generate(handoff.query, handoff.context))
        validation = validate_citations(answer, handoff)
        expected_abstain = reason is not None
        abstention_correct = answer.abstained == expected_abstain
        citation_correct = validation.valid and (answer.abstained or bool(validation.valid_citations))
        grounded = _judge(judges, "groundedness", case, answer, handoff.context) if not answer.abstained else expected_abstain
        correctness = _judge(judges, "correctness", case, answer) if not answer.abstained else expected_abstain
        utilized = len(set(validation.valid_citations)) / len(context_ids) if context_ids else 0.0
        counts["correctness"] += int(correctness)
        counts["faithfulness"] += int(grounded)
        counts["citation_correctness"] += int(citation_correct)
        counts["context_utilization"] += int(utilized > 0.0)
        counts["abstention_accuracy"] += int(abstention_correct)
        detail = {"case_id": case.case_id, **answer.to_dict(), "citation_valid": validation.valid,
                  "citation_issues": list(validation.issues), "evidence_ratio": ratio,
                  "correctness": correctness, "faithfulness": grounded,
                  "citation_correctness": citation_correct, "context_utilization": utilized}
        details.append(detail)
        if not validation.valid:
            failures.append({"type": "GENERATION_CITATION_ERROR", "case_id": case.case_id,
                             "details": {"issues": list(validation.issues)}})
    evaluated = len(details) or 1
    metrics = {name: value / evaluated for name, value in counts.items()}
    metrics["cases"] = len(details)
    report = GenerationReport(metrics, tuple(details), tuple(failures))
    if output is not None:
        destination = Path(output)
        destination.mkdir(parents=True, exist_ok=True)
        (destination / "generation_summary.json").write_text(json.dumps({"metrics": dict(metrics)}, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
        (destination / "generation_details.jsonl").write_text("".join(json.dumps(item, ensure_ascii=False, sort_keys=True) + "\n" for item in details), encoding="utf-8")
        (destination / "generation_failures.jsonl").write_text("".join(json.dumps(item, ensure_ascii=False, sort_keys=True) + "\n" for item in failures), encoding="utf-8")
    return report
