"""Deterministic ranked-result contract and offline retrieval evaluation."""

from __future__ import annotations

from dataclasses import dataclass, field
import json
from pathlib import Path
from typing import Any, Iterable, Mapping, Protocol, Sequence

DEFAULT_CUTOFFS = (1, 3, 5, 10, 20)


class RetrievalInputError(ValueError):
    """The retriever result cannot satisfy the ranked-result contract."""


@dataclass(frozen=True, slots=True)
class RankedChunk:
    """The evaluator-facing result returned by every retriever adapter."""

    chunk_id: str
    rank: int
    score: float | None = None
    metadata: Mapping[str, object] = field(default_factory=dict)


class RetrieverPort(Protocol):
    def retrieve(self, query: str, limit: int = 20) -> Sequence[RankedChunk | str]:
        """Return unique evaluator chunk IDs in descending rank order."""


@dataclass(frozen=True, slots=True)
class OfflineRankedIdRetriever:
    """Adapter for a JSON-compatible mapping of query to ranked fixtures."""

    ranked_ids: Mapping[str, Sequence[str | RankedChunk | Mapping[str, object]]]

    def retrieve(self, query: str, limit: int = 20) -> Sequence[RankedChunk | str]:
        if query not in self.ranked_ids:
            raise RetrievalInputError(f"missing query result: {query}")
        values = self.ranked_ids[query]
        if not isinstance(values, (list, tuple)):
            raise RetrievalInputError(f"invalid ordering for query: {query}")
        result: list[RankedChunk | str] = []
        for index, value in enumerate(values[:limit], 1):
            if isinstance(value, Mapping):
                try:
                    result.append(RankedChunk(str(value["chunk_id"]), int(value.get("rank", index)),
                                              value.get("score"), value.get("metadata", {})))
                except (KeyError, TypeError, ValueError) as exc:
                    raise RetrievalInputError(f"invalid ordering for query: {query}") from exc
            else:
                result.append(value)
        return tuple(result)


@dataclass(frozen=True, slots=True)
class RetrievalReport:
    recall_at: Mapping[int, float]
    mrr: float
    evidence_recall_at: Mapping[int, float]
    queries: int
    single_evidence_queries: int
    multi_evidence_queries: int
    details: tuple[dict[str, object], ...] = ()
    failures: tuple[dict[str, object], ...] = ()

    @property
    def evidence_recall_at_5(self) -> float:
        return self.evidence_recall_at.get(5, 0.0)

    @property
    def evidence_recall(self) -> float:
        return self.evidence_recall_at_5

    def to_dict(self) -> dict[str, object]:
        return {
            "cutoffs": list(self.recall_at),
            "recall_at": {str(k): value for k, value in self.recall_at.items()},
            **{f"recall_at_{k}": self.recall_at.get(k, 0.0) for k in DEFAULT_CUTOFFS},
            "evidence_recall_at": {str(k): value for k, value in self.evidence_recall_at.items()},
            **{f"evidence_recall_at_{k}": self.evidence_recall_at.get(k, 0.0) for k in DEFAULT_CUTOFFS},
            "evidence_recall": self.evidence_recall,
            "mrr": self.mrr, "queries": self.queries,
            "single_evidence_queries": self.single_evidence_queries,
            "multi_evidence_queries": self.multi_evidence_queries,
            "failures": list(self.failures),
        }


def _normalise_ranked(query: str, ranked: Sequence[RankedChunk | str], known: set[str] | None) -> tuple[RankedChunk, ...]:
    if not isinstance(ranked, (list, tuple)):
        raise RetrievalInputError(f"invalid ordering for query: {query}")
    if len(ranked) > 20:
        raise RetrievalInputError(f"invalid ordering for query: {query}: more than top-20")
    result: list[RankedChunk] = []
    for index, item in enumerate(ranked, 1):
        if isinstance(item, str):
            value = RankedChunk(item, index)
        elif isinstance(item, RankedChunk):
            value = item
            if value.rank != index:
                raise RetrievalInputError(f"invalid ordering for query: {query}: rank mismatch")
        else:
            raise RetrievalInputError(f"invalid ordering for query: {query}")
        if not value.chunk_id:
            raise RetrievalInputError(f"invalid ordering for query: {query}")
        result.append(value)
    ids = [item.chunk_id for item in result]
    if len(set(ids)) != len(ids):
        raise RetrievalInputError(f"duplicate result for query: {query}")
    if known is not None:
        unknown = sorted(set(ids) - known)
        if unknown:
            raise RetrievalInputError(f"unknown chunk ID for query {query}: {unknown}")
    return tuple(result)


def evaluate_ranked_retrieval(
    gold: Mapping[str, Iterable[str]],
    retrieved: Mapping[str, Sequence[RankedChunk | str]] | RetrieverPort,
    *,
    required_evidence: Mapping[str, Iterable[str]] | None = None,
    known_chunk_ids: Iterable[str] | None = None,
    ks: tuple[int, ...] = DEFAULT_CUTOFFS,
) -> RetrievalReport:
    """Evaluate Recall@K, MRR, and evidence completeness for one snapshot."""
    if not ks or any(k <= 0 or k > 20 for k in ks) or tuple(sorted(set(ks))) != ks:
        raise RetrievalInputError("invalid cutoff ordering")
    gold_sets = {str(query): frozenset(str(value) for value in values) for query, values in gold.items()}
    known = set(str(value) for value in known_chunk_ids) if known_chunk_ids is not None else None
    if isinstance(retrieved, Mapping):
        missing = sorted(set(gold_sets) - set(retrieved))
        if missing:
            raise RetrievalInputError(f"missing query result: {missing[0]}")
        unexpected = sorted(set(retrieved) - set(gold_sets))
        if unexpected:
            raise RetrievalInputError(f"unknown query result: {unexpected[0]}")
        ranked_by_query = {query: _normalise_ranked(query, retrieved[query], known) for query in gold_sets}
    else:
        ranked_by_query = {}
        for query in gold_sets:
            try:
                ranked_by_query[query] = _normalise_ranked(query, retrieved.retrieve(query, 20), known)
            except RetrievalInputError:
                raise
            except Exception as exc:
                raise RetrievalInputError(f"missing query result: {query}") from exc

    scores = {k: 0.0 for k in ks}
    evidence_scores = {k: 0.0 for k in ks}
    reciprocal = 0.0
    single = multi = 0
    details: list[dict[str, object]] = []
    for query, expected in gold_sets.items():
        ids = tuple(item.chunk_id for item in ranked_by_query[query])
        evidence = frozenset(str(value) for value in (required_evidence or {}).get(query, expected))
        if len(evidence) <= 1:
            single += 1
        else:
            multi += 1
        first = next((index for index, item in enumerate(ids, 1) if item in expected), None)
        if first is not None:
            reciprocal += 1 / first
        per_case = {str(k): (len(evidence.intersection(ids[:k])) / len(evidence) if evidence else 0.0) for k in ks}
        for k in ks:
            scores[k] += bool(expected.intersection(ids[:k]))
            evidence_scores[k] += per_case[str(k)]
        details.append({"case_id": query, "ranked_chunk_ids": list(ids), "first_gold_rank": first,
                        "evidence_chunk_ids": sorted(evidence), "evidence_completeness": per_case[str(max(ks))],
                        "evidence_recall_at": per_case})
    count = len(gold_sets) or 1
    return RetrievalReport({k: value / count for k, value in scores.items()}, reciprocal / count,
                           {k: value / count for k, value in evidence_scores.items()}, len(gold_sets),
                           single, multi, tuple(details))


def write_retrieval_artifacts(report: RetrievalReport | Mapping[str, object], output: str | Path, experiment: str,
                              metadata: Mapping[str, object] | None = None) -> tuple[Path, Path]:
    """Write the stable experiment summary and per-case JSONL details."""
    destination = Path(output)
    destination.mkdir(parents=True, exist_ok=True)
    summary = destination / f"retrieval_{experiment}_summary.json"
    details = destination / f"retrieval_{experiment}_details.jsonl"
    report_value = report.to_dict() if isinstance(report, RetrievalReport) else dict(report)
    value: dict[str, Any] = {"experiment": experiment, **report_value, **dict(metadata or {})}
    summary.write_text(json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    report_details = report.details if isinstance(report, RetrievalReport) else report.get("details", ())
    details.write_text("".join(json.dumps(item, ensure_ascii=False, sort_keys=True) + "\n" for item in report_details), encoding="utf-8")
    return summary, details


def evaluate_retrieval(gold: Mapping[str, Iterable[str]], retrieved: Mapping[str, Sequence[RankedChunk | str]] | RetrieverPort,
                       ks: tuple[int, ...] = DEFAULT_CUTOFFS) -> RetrievalReport:
    return evaluate_ranked_retrieval(gold, retrieved, ks=ks)
