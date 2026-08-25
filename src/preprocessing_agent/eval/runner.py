from __future__ import annotations
from dataclasses import dataclass
from typing import Any, Callable, Iterable, Mapping
from .metrics import EvaluationReport, evaluate_retrieval
@dataclass(frozen=True, slots=True)
class ExperimentResult:
    reports: Mapping[str, EvaluationReport]
    def to_dict(self) -> dict[str, object]:
        return {name: report.to_dict() for name, report in self.reports.items()}
def run_experiments(policies: Mapping[str, Any], gold: Mapping[str, Iterable[str]], retriever: Callable[[Any], Mapping[str, Iterable[str]]] | Mapping[str, Mapping[str, Iterable[str]]]) -> ExperimentResult:
    reports = {}
    for name, policy in policies.items():
        retrieved = retriever(policy) if callable(retriever) else retriever[name]
        reports[name] = evaluate_retrieval(gold, retrieved)
    return ExperimentResult(reports)
