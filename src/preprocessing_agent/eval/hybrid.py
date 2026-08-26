"""Injected Reciprocal Rank Fusion comparison for retrieval baselines."""

from __future__ import annotations

from dataclasses import dataclass
import hashlib
import json
from pathlib import Path
from typing import Iterable, Mapping, Sequence

from preprocessing_agent.domain import Chunk

from .gold import GoldCase
from .retrieval import (
    DEFAULT_CUTOFFS,
    RankedChunk,
    RetrievalInputError,
    RetrievalReport,
    RetrieverPort,
    evaluate_ranked_retrieval,
)

FAILURE_TAXONOMY = (
    "RETRIEVAL_MISS", "RANKING_ERROR", "CHUNK_BOUNDARY", "QUERY_MISMATCH",
    "METADATA_MISMATCH", "MULTI_EVIDENCE_MISS", "TABLE_RETRIEVAL_FAILURE",
)


def _source_name(item: RankedChunk, fallback: str) -> str:
    value = item.metadata.get("retriever") if isinstance(item.metadata, Mapping) else None
    return str(value or fallback)


@dataclass(frozen=True, slots=True)
class RrfHybridRetriever:
    """Combine two ranked retrievers without depending on either index implementation."""

    dense: RetrieverPort
    bm25: RetrieverPort
    rrf_k: int = 60

    def __post_init__(self) -> None:
        if self.rrf_k < 1:
            raise RetrievalInputError("rrf_k must be positive")

    def retrieve(self, query: str, limit: int = 20) -> tuple[RankedChunk, ...]:
        if limit <= 0:
            return ()
        streams = (
            ("dense", tuple(self.dense.retrieve(query, limit))),
            ("bm25", tuple(self.bm25.retrieve(query, limit))),
        )
        fused: dict[str, dict[str, object]] = {}
        for fallback, ranked in streams:
            seen: set[str] = set()
            for position, item in enumerate(ranked, 1):
                if isinstance(item, str):
                    item = RankedChunk(item, position)
                if not isinstance(item, RankedChunk) or not item.chunk_id:
                    raise RetrievalInputError(f"invalid ordering for query: {query}")
                if item.chunk_id in seen:
                    raise RetrievalInputError(f"duplicate result for query: {query}")
                seen.add(item.chunk_id)
                entry = fused.setdefault(item.chunk_id, {"score": 0.0, "sources": set(), "metadata": {}})
                entry["score"] = float(entry["score"]) + 1 / (self.rrf_k + position)
                entry["sources"].add(_source_name(item, fallback))
                if isinstance(item.metadata, Mapping):
                    entry["metadata"].update(item.metadata)
        ordered = sorted(fused.items(), key=lambda pair: (-float(pair[1]["score"]), pair[0]))[:limit]
        return tuple(
            RankedChunk(chunk_id, rank, float(value["score"]), {
                **dict(value["metadata"]), "retriever": "hybrid_rrf",
                "sources": sorted(value["sources"]), "rrf_k": self.rrf_k,
            })
            for rank, (chunk_id, value) in enumerate(ordered, 1)
        )


def classify_retrieval_failure(*, query: str, ranked_ids: Sequence[str], gold_ids: Sequence[str],
                               evidence_ids: Sequence[str], metadata: Mapping[str, object] | None = None,
                               ranking_error: bool = False) -> str | None:
    """Apply the stable failure decision table; return None for a successful case."""
    metadata = metadata or {}
    ranked = tuple(ranked_ids)
    gold = frozenset(gold_ids)
    evidence = frozenset(evidence_ids)
    if ranking_error or len(set(ranked)) != len(ranked):
        return "RANKING_ERROR"
    content_type = str(metadata.get("content_type", "")).casefold()
    if content_type == "table" or bool(metadata.get("table")):
        return "TABLE_RETRIEVAL_FAILURE" if not evidence.intersection(ranked) else None
    if bool(metadata.get("metadata_mismatch")):
        return "METADATA_MISMATCH"
    if bool(metadata.get("chunk_boundary")):
        return "CHUNK_BOUNDARY"
    if len(evidence) > 1 and not evidence.issubset(ranked):
        return "MULTI_EVIDENCE_MISS"
    if not gold.intersection(ranked):
        if metadata.get("query_mismatch") or not any(str(term).casefold() in " ".join(ranked).casefold()
                                                       for term in str(query).split() if len(term) > 2):
            return "QUERY_MISMATCH" if metadata.get("query_mismatch") else "RETRIEVAL_MISS"
        return "RETRIEVAL_MISS"
    return None


def _gold_ids(cases: Sequence[GoldCase], chunks: Sequence[Chunk]) -> tuple[dict[str, tuple[str, ...]], dict[str, tuple[str, ...]]]:
    by_key = {chunk.canonical_key: chunk.chunk_id for chunk in chunks}
    gold: dict[str, tuple[str, ...]] = {}
    evidence: dict[str, tuple[str, ...]] = {}
    for case in cases:
        if case.answerable is False:
            continue
        ids = case.gold_chunk_ids or tuple(by_key[key] for key in case.gold_context_keys if key in by_key)
        groups = case.evidence_groups or case.required_evidence
        values = tuple(value for group in groups for value in group.keys)
        gold[case.case_id] = ids
        evidence[case.case_id] = tuple(by_key.get(value, value) for value in (values or ids))
    return gold, evidence


@dataclass(frozen=True, slots=True)
class HybridBaselineResult:
    report: RetrievalReport
    summary_path: Path
    details_path: Path
    failure_path: Path
    comparison_path: Path | None = None


def run_hybrid_baseline(chunks: Iterable[Chunk], cases: Iterable[GoldCase], dense: RetrieverPort,
                        bm25: RetrieverPort, output: str | Path, *, source_run_hash: str = "",
                        gold_snapshot_hash: str = "", rrf_k: int = 60) -> HybridBaselineResult:
    chunks, cases = tuple(chunks), tuple(cases)
    hybrid = RrfHybridRetriever(dense, bm25, rrf_k)
    gold, evidence = _gold_ids(cases, chunks)
    ranked = {case_id: hybrid.retrieve(next(case.question for case in cases if case.case_id == case_id), 20)
              for case_id in gold}
    report = evaluate_ranked_retrieval(gold, ranked, required_evidence=evidence,
                                       known_chunk_ids=(chunk.chunk_id for chunk in chunks))
    destination = Path(output)
    destination.mkdir(parents=True, exist_ok=True)
    source_hash = source_run_hash or hashlib.sha256(json.dumps([(c.chunk_id, c.embedding_text) for c in chunks], sort_keys=True).encode()).hexdigest()
    gold_hash = gold_snapshot_hash or hashlib.sha256(json.dumps([case.case_id for case in cases]).encode()).hexdigest()
    metadata = {"status": "completed", "source_run_hash": source_hash, "gold_snapshot_hash": gold_hash,
                "provider": "rrf", "rrf_k": rrf_k, "indexed_chunks": len(chunks)}
    failures: list[dict[str, object]] = []
    questions = {case.case_id: case.question for case in cases}
    for detail in report.details:
        case_id = str(detail["case_id"])
        failure_type = classify_retrieval_failure(query=questions[case_id], ranked_ids=detail["ranked_chunk_ids"],
                                                  gold_ids=gold[case_id], evidence_ids=evidence[case_id])
        if failure_type:
            failures.append({"case_id": case_id, "failure_type": failure_type,
                             "found_evidence": sorted(set(detail["ranked_chunk_ids"]) & set(evidence[case_id])),
                             "missing_evidence": sorted(set(evidence[case_id]) - set(detail["ranked_chunk_ids"])),
                             "ranked_chunk_ids": detail["ranked_chunk_ids"], "query": questions[case_id]})
    summary_path = destination / "retrieval_hybrid_summary.json"
    summary_path.write_text(json.dumps({"experiment": "hybrid", **report.to_dict(), **metadata}, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    details_path = destination / "retrieval_hybrid_details.jsonl"
    details_path.write_text("".join(json.dumps({**detail, **{key: metadata[key] for key in ("source_run_hash", "gold_snapshot_hash")}}, ensure_ascii=False, sort_keys=True) + "\n" for detail in report.details), encoding="utf-8")
    failure_path = destination / "retrieval_failures.jsonl"
    failure_path.write_text("".join(json.dumps(item, ensure_ascii=False, sort_keys=True) + "\n" for item in failures), encoding="utf-8")
    return HybridBaselineResult(report, summary_path, details_path, failure_path)


def _experiment(value: Mapping[str, object]) -> dict[str, object]:
    if value.get("status", "completed") != "completed":
        raise RetrievalInputError(f"experiment {value.get('experiment', '<unknown>')} is not completed")
    required = ("source_run_hash", "gold_snapshot_hash", "cutoffs")
    if any(key not in value for key in required):
        raise RetrievalInputError("experiment is missing snapshot metadata")
    if tuple(value["cutoffs"]) != DEFAULT_CUTOFFS:
        raise RetrievalInputError("experiment cutoffs do not match snapshot")
    return dict(value)


def compare_retrieval_experiments(dense: Mapping[str, object], bm25: Mapping[str, object],
                                  hybrid: Mapping[str, object]) -> dict[str, object]:
    experiments = {name: _experiment(value) for name, value in (("dense", dense), ("bm25", bm25), ("hybrid", hybrid))}
    snapshots = {(value["source_run_hash"], value["gold_snapshot_hash"]) for value in experiments.values()}
    if len(snapshots) != 1:
        raise RetrievalInputError("experiments use different snapshot metadata")
    metrics = {name: {metric: float(value.get(metric, 0.0)) for metric in ("recall_at_1", "recall_at_3", "recall_at_5", "recall_at_10", "recall_at_20", "mrr", "evidence_recall")} for name, value in experiments.items()}
    return {"experiments": experiments, "metrics": metrics,
            "snapshot": {"source_run_hash": next(iter(snapshots))[0], "gold_snapshot_hash": next(iter(snapshots))[1], "cutoffs": list(DEFAULT_CUTOFFS)},
            "winner": None}
