"""Injected reranking, bounded context expansion, and generation handoff contracts."""

from __future__ import annotations

from dataclasses import dataclass, field
import math
from typing import Callable, Iterable, Mapping, Protocol, Sequence

from preprocessing_agent.domain import Chunk

from .gold import GoldCase
from .hybrid import _gold_ids
from .retrieval import RankedChunk, RetrievalInputError, RetrieverPort

RERANK_CANDIDATE_LIMIT = 30
RERANK_OUTPUT_LIMIT = 5
OFFLINE_RERANKER_ADAPTER = {"mode": "offline", "live_java_adapter": False}


class RerankerPort(Protocol):
    def rerank(self, query: str, candidates: Sequence[RankedChunk], limit: int = RERANK_OUTPUT_LIMIT) -> Sequence[RankedChunk]:
        """Reorder at most the first 30 candidates and return at most five."""


ScoreFunction = Callable[[str, RankedChunk], float]


@dataclass(frozen=True, slots=True)
class InjectedReranker:
    """Offline adapter whose scoring function is injected by the experiment."""

    scorer: ScoreFunction
    candidate_limit: int = RERANK_CANDIDATE_LIMIT
    output_limit: int = RERANK_OUTPUT_LIMIT
    adapter_metadata: Mapping[str, object] = field(default_factory=lambda: dict(OFFLINE_RERANKER_ADAPTER))

    def __post_init__(self) -> None:
        if self.candidate_limit != RERANK_CANDIDATE_LIMIT or self.output_limit != RERANK_OUTPUT_LIMIT:
            raise RetrievalInputError("RAG-006 reranker limits must be top-30 to top-5")
        if not callable(self.scorer):
            raise RetrievalInputError("reranker scorer must be callable")

    def rerank(self, query: str, candidates: Sequence[RankedChunk], limit: int = RERANK_OUTPUT_LIMIT) -> tuple[RankedChunk, ...]:
        if not isinstance(candidates, (list, tuple)):
            raise RetrievalInputError("reranker candidates must be a sequence")
        if limit <= 0 or limit > self.output_limit:
            raise RetrievalInputError("reranker output limit must be between 1 and 5")
        window = tuple(candidates[:self.candidate_limit])
        if len({item.chunk_id for item in window}) != len(window):
            raise RetrievalInputError(f"duplicate reranker candidate for query: {query}")
        scored: list[tuple[float, RankedChunk, int]] = []
        for position, candidate in enumerate(window, 1):
            if not isinstance(candidate, RankedChunk) or not candidate.chunk_id:
                raise RetrievalInputError(f"invalid reranker candidate for query: {query}")
            try:
                score = float(self.scorer(query, candidate))
            except (TypeError, ValueError) as exc:
                raise RetrievalInputError(f"invalid reranker score for query: {query}") from exc
            if not math.isfinite(score):
                raise RetrievalInputError(f"invalid reranker score for query: {query}")
            scored.append((score, candidate, position))
        ordered = sorted(scored, key=lambda item: (-item[0], item[1].chunk_id))[:limit]
        return tuple(
            RankedChunk(item.chunk_id, rank, score, {
                **dict(item.metadata), "retriever": "reranker", "rerank_score": score,
            "candidate_rank": _, "candidate_limit": self.candidate_limit,
            })
            for rank, (score, item, _) in enumerate(ordered, 1)
        )


@dataclass(frozen=True, slots=True)
class RerankedReport:
    reranked_recall_at_5: float
    mrr: float
    ndcg_at_5: float
    queries: int
    details: tuple[dict[str, object], ...] = ()

    @property
    def recall_at_5(self) -> float:
        return self.reranked_recall_at_5

    def to_dict(self) -> dict[str, object]:
        return {"reranked_recall_at_5": self.reranked_recall_at_5, "recall_at_5": self.reranked_recall_at_5,
                "mrr": self.mrr, "ndcg_at_5": self.ndcg_at_5, "queries": self.queries,
                "details": list(self.details)}


def _first_gold(ranked: Sequence[RankedChunk], gold: frozenset[str]) -> int | None:
    return next((index for index, item in enumerate(ranked, 1) if item.chunk_id in gold), None)


def _ndcg_at_5(ranked: Sequence[RankedChunk], gold: frozenset[str]) -> float:
    actual = sum(1 / math.log2(index + 2) for index, item in enumerate(ranked[:5]) if item.chunk_id in gold)
    ideal_count = min(5, len(gold))
    ideal = sum(1 / math.log2(index + 2) for index in range(ideal_count))
    return actual / ideal if ideal else 0.0


def evaluate_reranked_retrieval(gold: Mapping[str, Iterable[str]], baseline: Mapping[str, Sequence[RankedChunk]],
                                reranked: Mapping[str, Sequence[RankedChunk]]) -> RerankedReport:
    if set(gold) != set(baseline) or set(gold) != set(reranked):
        raise RetrievalInputError("baseline and reranked results must cover the same queries")
    details: list[dict[str, object]] = []
    recall = reciprocal = ndcg = 0.0
    for query in gold:
        expected = frozenset(str(value) for value in gold[query])
        before, after = tuple(baseline[query]), tuple(reranked[query])
        first_before, first_after = _first_gold(before, expected), _first_gold(after, expected)
        recall += bool(expected.intersection(item.chunk_id for item in after[:5]))
        reciprocal += 1 / first_after if first_after is not None else 0.0
        ndcg += _ndcg_at_5(after, expected)
        details.append({"case_id": query, "baseline_first_gold_rank": first_before,
                        "reranked_first_gold_rank": first_after,
                        "baseline_ranked_chunk_ids": [item.chunk_id for item in before],
                        "reranked_chunk_ids": [item.chunk_id for item in after]})
    count = len(gold) or 1
    return RerankedReport(recall / count, reciprocal / count, ndcg / count, len(gold), tuple(details))


@dataclass(frozen=True, slots=True)
class ContextExpansionPolicy:
    max_parent_depth: int = 1
    max_items: int = 8

    def __post_init__(self) -> None:
        if self.max_parent_depth < 0 or self.max_items < 1:
            raise ValueError("context expansion bounds must be non-negative and non-zero")


@dataclass(frozen=True, slots=True)
class ContextItem:
    chunk_id: str
    source_text: str
    metadata: Mapping[str, object] = field(default_factory=dict)


@dataclass(frozen=True, slots=True)
class ExpandedContext:
    items: tuple[ContextItem, ...]
    retrieval_gold_ids: tuple[str, ...]


def _citation(chunk: Chunk) -> str:
    return chunk.canonical_key or chunk.chunk_id


def _locator(chunk: Chunk) -> dict[str, object] | None:
    if not chunk.source_spans:
        return None
    span = chunk.source_spans[0]
    return {"page_number": span.page_number, "block_index": span.block_index,
            "char_start": span.char_start, "char_end": span.char_end}


def _item(chunk: Chunk, metadata: Mapping[str, object]) -> ContextItem:
    return ContextItem(chunk.chunk_id, chunk.source_text, {
        "source_citation": _citation(chunk), "section_path": list(chunk.section_path),
        "locator": _locator(chunk),
        **dict(metadata),
    })


def expand_parent_context(ranked: Sequence[RankedChunk], chunks: Iterable[Chunk],
                          policy: ContextExpansionPolicy = ContextExpansionPolicy()) -> ExpandedContext:
    by_key = {key: chunk for chunk in chunks for key in (chunk.chunk_id, chunk.canonical_key)}
    items: list[ContextItem] = []
    included: set[str] = set()
    retrieval_ids = tuple(item.chunk_id for item in ranked)
    for candidate in ranked:
        chunk = by_key.get(candidate.chunk_id)
        if chunk is None or len(items) >= policy.max_items:
            continue
        if chunk.chunk_id not in included:
            items.append(_item(chunk, {"relation": "retrieved", "retrieval_rank": candidate.rank,
                                       "retrieval_metadata": dict(candidate.metadata)}))
            included.add(chunk.chunk_id)
        parent_key = chunk.parent_key
        for _ in range(policy.max_parent_depth):
            if len(items) >= policy.max_items or not parent_key:
                break
            parent = by_key.get(parent_key)
            if parent is None or parent.chunk_id in included:
                break
            items.append(_item(parent, {"relation": "parent", "child_chunk_id": chunk.chunk_id}))
            included.add(parent.chunk_id)
            parent_key = parent.parent_key
    return ExpandedContext(tuple(items), retrieval_ids)


@dataclass(frozen=True, slots=True)
class GenerationHandoff:
    query: str
    context: tuple[dict[str, object], ...]
    citations: tuple[dict[str, object], ...]
    retrieval_gold_ids: tuple[str, ...]
    adapter_metadata: Mapping[str, object]


def build_generation_handoff(query: str, expanded: ExpandedContext,
                             evaluator_to_java: Mapping[str, object] | None = None) -> GenerationHandoff:
    mapping = evaluator_to_java or {}
    context: list[dict[str, object]] = []
    citations: list[dict[str, object]] = []
    for item in expanded.items:
        value = {"chunk_id": item.chunk_id, "text": item.source_text, **dict(item.metadata)}
        context.append(value)
        citation: dict[str, object] = {"chunk_id": item.chunk_id, "source_citation": item.metadata.get("source_citation"),
                                       "section_path": item.metadata.get("section_path"), "relation": item.metadata.get("relation"),
                                       "locator": item.metadata.get("locator")}
        java_value = mapping.get(item.chunk_id)
        if isinstance(java_value, Mapping):
            citation.update(java_value)
        elif java_value is not None:
            citation["java_uuid"] = java_value
        citations.append(citation)
    return GenerationHandoff(query, tuple(context), tuple(citations), expanded.retrieval_gold_ids,
                             {**dict(OFFLINE_RERANKER_ADAPTER), "acl_mapping": "evaluator_chunk_id_to_java_uuid_or_locator"})


def run_reranker_evaluation(chunks: Iterable[Chunk], cases: Iterable[GoldCase], hybrid: RetrieverPort,
                            reranker: RerankerPort, output: str, *, source_run_hash: str = "",
                            gold_snapshot_hash: str = "") -> tuple[dict[str, object], object]:
    """Evaluate a top-30 hybrid stream and persist rerank/context handoff artifacts."""
    from pathlib import Path
    import json

    chunks, cases = tuple(chunks), tuple(cases)
    gold, _ = _gold_ids(cases, chunks)
    questions = {case.case_id: case.question for case in cases}
    baseline = {case_id: tuple(hybrid.retrieve(questions[case_id], RERANK_CANDIDATE_LIMIT)) for case_id in gold}
    reranked = {case_id: tuple(reranker.rerank(questions[case_id], baseline[case_id], RERANK_OUTPUT_LIMIT)) for case_id in gold}
    report = evaluate_reranked_retrieval(gold, baseline, reranked)
    destination = Path(output)
    destination.mkdir(parents=True, exist_ok=True)
    metadata = {"experiment": "hybrid_reranker", "status": "completed", "candidate_limit": RERANK_CANDIDATE_LIMIT,
                "output_limit": RERANK_OUTPUT_LIMIT, "source_run_hash": source_run_hash,
                "gold_snapshot_hash": gold_snapshot_hash, "adapter": dict(OFFLINE_RERANKER_ADAPTER)}
    summary = {**report.to_dict(), **metadata}
    (destination / "retrieval_reranked_summary.json").write_text(json.dumps(summary, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    (destination / "retrieval_reranked_details.jsonl").write_text("".join(json.dumps(item, ensure_ascii=False, sort_keys=True) + "\n" for item in report.details), encoding="utf-8")
    handoffs = []
    for case_id, values in reranked.items():
        expanded = expand_parent_context(values, chunks)
        handoffs.append(build_generation_handoff(questions[case_id], expanded))
    handoff_path = destination / "generation_reranked_handoff.jsonl"
    handoff_path.write_text("".join(json.dumps({"case_id": case_id, **_handoff_dict(handoff)}, ensure_ascii=False, sort_keys=True) + "\n"
                                    for case_id, handoff in zip(reranked, handoffs)), encoding="utf-8")
    return summary, handoff_path


def _handoff_dict(handoff: GenerationHandoff) -> dict[str, object]:
    return {"query": handoff.query, "context": list(handoff.context), "citations": list(handoff.citations),
            "retrieval_gold_ids": list(handoff.retrieval_gold_ids), "adapter_metadata": dict(handoff.adapter_metadata)}
