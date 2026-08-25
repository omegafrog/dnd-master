"""Deterministic retrieval evaluation over ranked evaluator chunk IDs.

The evaluator deliberately knows nothing about Java UUIDs, locators, HTTP, or
vector stores.  Those concerns belong in an adapter implementing
``RetrieverPort`` and returning the ranked IDs used by exported chunks.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Iterable, Mapping, Protocol, Sequence


class RetrievalInputError(ValueError):
    """The retriever result cannot satisfy the ranked-ID input contract."""


class RetrieverPort(Protocol):
    """Boundary for a retriever that returns IDs in descending rank order."""

    def retrieve(self, query: str, limit: int = 10) -> Sequence[str]:
        """Return at most ``limit`` evaluator chunk IDs, already ranked."""


@dataclass(frozen=True, slots=True)
class OfflineRankedIdRetriever:
    """A deterministic adapter for recorded ranked results and unit tests."""

    ranked_ids: Mapping[str, Sequence[str]]

    def retrieve(self, query: str, limit: int = 10) -> Sequence[str]:
        if query not in self.ranked_ids:
            raise RetrievalInputError(f"missing query result: {query}")
        values = self.ranked_ids[query]
        if not isinstance(values, (list, tuple)):
            raise RetrievalInputError(f"invalid ordering for query: {query}")
        return tuple(values[:limit])


@dataclass(frozen=True, slots=True)
class RetrievalReport:
    recall_at: Mapping[int, float]
    mrr: float
    evidence_recall_at_5: float
    queries: int
    single_evidence_queries: int
    multi_evidence_queries: int
    failures: tuple[dict[str, object], ...] = ()

    @property
    def evidence_recall(self) -> float:
        """Compatibility alias used by the earlier metric contract."""

        return self.evidence_recall_at_5

    def to_dict(self) -> dict[str, object]:
        return {
            "recall_at": {str(k): value for k, value in self.recall_at.items()},
            "recall_at_1": self.recall_at.get(1, 0.0),
            "recall_at_3": self.recall_at.get(3, 0.0),
            "recall_at_5": self.recall_at.get(5, 0.0),
            "recall_at_10": self.recall_at.get(10, 0.0),
            "mrr": self.mrr,
            "evidence_recall_at_5": self.evidence_recall_at_5,
            "evidence_recall": self.evidence_recall_at_5,
            "queries": self.queries,
            "single_evidence_queries": self.single_evidence_queries,
            "multi_evidence_queries": self.multi_evidence_queries,
            "failures": list(self.failures),
        }


def _failure(kind: str, query: str, details: object) -> dict[str, object]:
    return {"type": kind, "case_id": query, "chunk_ids": [], "details": details}


def _validate_ranked_ids(
    query: str,
    ranked: Sequence[str],
    known_chunk_ids: set[str] | None,
) -> tuple[str, ...]:
    if not isinstance(ranked, (list, tuple)):
        raise RetrievalInputError(f"invalid ordering for query: {query}")
    values = tuple(ranked)
    if len(values) > 10:
        raise RetrievalInputError(f"invalid ordering for query: {query}: more than top-10")
    if any(not isinstance(value, str) or not value for value in values):
        raise RetrievalInputError(f"invalid ordering for query: {query}")
    if len(set(values)) != len(values):
        raise RetrievalInputError(f"duplicate result for query: {query}")
    if known_chunk_ids is not None:
        unknown = sorted(set(values) - known_chunk_ids)
        if unknown:
            raise RetrievalInputError(f"unknown chunk ID for query {query}: {unknown}")
    return values


def evaluate_ranked_retrieval(
    gold: Mapping[str, Iterable[str]],
    retrieved: Mapping[str, Sequence[str]] | RetrieverPort,
    *,
    required_evidence: Mapping[str, Iterable[str]] | None = None,
    known_chunk_ids: Iterable[str] | None = None,
    ks: tuple[int, ...] = (1, 3, 5, 10),
) -> RetrievalReport:
    """Evaluate exact query coverage and ranked evaluator chunk IDs.

    ``required_evidence`` may contain multiple IDs per query.  Evidence Recall
    is the mean fraction of that query's required IDs present in its top five;
    a query without explicit evidence uses its gold IDs.  Input failures are
    raised before any partial score is returned.
    """

    if not ks or any(k <= 0 or k > 10 for k in ks) or tuple(sorted(set(ks))) != ks:
        raise RetrievalInputError("invalid cutoff ordering")
    gold_sets = {query: frozenset(str(value) for value in values) for query, values in gold.items()}
    known = set(known_chunk_ids) if known_chunk_ids is not None else None
    if isinstance(retrieved, Mapping):
        missing = sorted(set(gold_sets) - set(retrieved))
        if missing:
            raise RetrievalInputError(f"missing query result: {missing[0]}")
        unexpected = sorted(set(retrieved) - set(gold_sets))
        if unexpected:
            raise RetrievalInputError(f"unknown query result: {unexpected[0]}")
        ranked_by_query = {query: _validate_ranked_ids(query, retrieved[query], known) for query in gold_sets}
    else:
        ranked_by_query = {}
        for query in gold_sets:
            try:
                ranked_by_query[query] = _validate_ranked_ids(query, retrieved.retrieve(query, 10), known)
            except RetrievalInputError:
                raise
            except Exception as exc:
                raise RetrievalInputError(f"missing query result: {query}") from exc

    scores = {k: 0.0 for k in ks}
    reciprocal = 0.0
    evidence_score = 0.0
    single = multi = 0
    for query, expected in gold_sets.items():
        ranked = ranked_by_query[query]
        evidence = frozenset(str(value) for value in (required_evidence or {}).get(query, expected))
        if len(evidence) <= 1:
            single += 1
        else:
            multi += 1
        for k in ks:
            scores[k] += bool(expected.intersection(ranked[:k]))
        first = next((index for index, item in enumerate(ranked, 1) if item in expected), None)
        if first is not None:
            reciprocal += 1 / first
        evidence_score += len(evidence.intersection(ranked[:5])) / len(evidence) if evidence else 0.0
    count = len(gold_sets) or 1
    return RetrievalReport({k: value / count for k, value in scores.items()}, reciprocal / count,
                           evidence_score / count, len(gold_sets), single, multi)


def evaluate_retrieval(
    gold: Mapping[str, Iterable[str]],
    retrieved: Mapping[str, Sequence[str]] | RetrieverPort,
    ks: tuple[int, ...] = (1, 3, 5, 10),
) -> RetrievalReport:
    """Short public entry point for the ranked-ID retrieval contract."""

    return evaluate_ranked_retrieval(gold, retrieved, ks=ks)
