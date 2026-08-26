"""Injected Dense retrieval adapter and reproducible baseline runner."""

from __future__ import annotations

from dataclasses import dataclass, field
import hashlib
import json
import math
from pathlib import Path
from typing import Iterable, Protocol, Sequence

from preprocessing_agent.domain import Chunk

from .gold import GoldCase
from .retrieval import DEFAULT_CUTOFFS, RankedChunk, RetrievalReport, evaluate_ranked_retrieval


class DenseProviderUnavailable(RuntimeError):
    """Dense evaluation cannot run because no embedding provider was injected."""


class DenseEmbeddingError(ValueError):
    """An embedding provider returned an unusable vector."""


class EmbeddingProvider(Protocol):
    def embed(self, text: str) -> Sequence[float]:
        """Embed one piece of text."""


def _vector(value: Sequence[float], *, label: str) -> tuple[float, ...]:
    try:
        result = tuple(float(item) for item in value)
    except (TypeError, ValueError) as exc:
        raise DenseEmbeddingError(f"embedding provider returned an invalid vector for {label}") from exc
    if not result or any(not math.isfinite(item) for item in result):
        raise DenseEmbeddingError(f"embedding provider returned an invalid vector for {label}")
    return result


def _cosine(left: tuple[float, ...], right: tuple[float, ...]) -> float:
    if len(left) != len(right):
        raise DenseEmbeddingError("embedding provider returned inconsistent vector dimensions")
    denominator = math.sqrt(sum(value * value for value in left) * sum(value * value for value in right))
    return 0.0 if denominator == 0.0 else sum(a * b for a, b in zip(left, right)) / denominator


@dataclass(slots=True)
class DenseEmbeddingIndexAdapter:
    """In-memory Dense index; the provider and index are deliberately injectable."""

    provider: EmbeddingProvider
    _vectors: dict[str, tuple[float, ...]] = field(init=False, repr=False)
    _chunks: dict[str, Chunk] = field(init=False, repr=False)

    def __post_init__(self) -> None:
        if self.provider is None or not callable(getattr(self.provider, "embed", None)):
            raise DenseProviderUnavailable("Dense embedding provider is unavailable; inject an EmbeddingProvider")
        self._vectors: dict[str, tuple[float, ...]] = {}
        self._chunks: dict[str, Chunk] = {}

    def index(self, chunks: Iterable[Chunk]) -> None:
        self._vectors.clear()
        self._chunks.clear()
        for chunk in chunks:
            vector = _vector(self.provider.embed(chunk.embedding_text), label=f"chunk {chunk.chunk_id}")
            if chunk.chunk_id in self._vectors:
                raise DenseEmbeddingError(f"duplicate chunk ID in Dense index: {chunk.chunk_id}")
            self._vectors[chunk.chunk_id] = vector
            self._chunks[chunk.chunk_id] = chunk

    def retrieve(self, query: str, limit: int = 20) -> tuple[RankedChunk, ...]:
        if limit <= 0:
            return ()
        query_vector = _vector(self.provider.embed(query), label="query")
        ranked = sorted(
            ((chunk_id, _cosine(query_vector, vector)) for chunk_id, vector in self._vectors.items()),
            key=lambda item: (-item[1], item[0]),
        )[:limit]
        return tuple(RankedChunk(chunk_id, rank, score, {"retriever": "dense"})
                     for rank, (chunk_id, score) in enumerate(ranked, 1))


@dataclass(frozen=True, slots=True)
class DenseBaselineResult:
    report: RetrievalReport
    summary_path: Path
    details_path: Path


def _snapshot_hash(cases: Sequence[GoldCase]) -> str:
    payload = [case.__dict__ if hasattr(case, "__dict__") else {
        "case_id": case.case_id, "question": case.question, "gold_chunk_ids": list(case.gold_chunk_ids),
        "gold_context_keys": list(case.gold_context_keys),
        "evidence_groups": [{"group_id": group.group_id, "keys": list(group.keys)} for group in case.evidence_groups],
    } for case in cases]
    return hashlib.sha256(json.dumps(payload, ensure_ascii=False, sort_keys=True, default=str).encode()).hexdigest()


def _gold_ids(cases: Sequence[GoldCase], chunks: Sequence[Chunk]) -> tuple[dict[str, tuple[str, ...]], dict[str, tuple[str, ...]]]:
    by_key = {chunk.canonical_key: chunk.chunk_id for chunk in chunks}
    gold: dict[str, tuple[str, ...]] = {}
    evidence: dict[str, tuple[str, ...]] = {}
    for case in cases:
        if case.answerable is False:
            continue
        ids = case.gold_chunk_ids or tuple(by_key[key] for key in case.gold_context_keys if key in by_key)
        gold[case.case_id] = ids
        groups = case.evidence_groups or case.required_evidence
        values = tuple(value for group in groups for value in group.keys)
        evidence[case.case_id] = tuple(by_key.get(value, value) for value in (values or ids))
    return gold, evidence


def run_dense_baseline(
    chunks: Iterable[Chunk], cases: Iterable[GoldCase], provider: EmbeddingProvider | None,
    output: str | Path, *, source_run_hash: str = "", gold_snapshot_hash: str = "",
    provider_name: str | None = None,
) -> DenseBaselineResult:
    """Index clean chunks and persist the Dense report using the retrieval contract."""
    chunks = tuple(chunks)
    cases = tuple(cases)
    adapter = DenseEmbeddingIndexAdapter(provider)  # explicit failure when provider is absent
    adapter.index(chunks)
    gold, evidence = _gold_ids(cases, chunks)
    ranked = {case.case_id: adapter.retrieve(case.question, 20) for case in cases if case.answerable is not False}
    report = evaluate_ranked_retrieval(gold, ranked, required_evidence=evidence,
                                       known_chunk_ids=(chunk.chunk_id for chunk in chunks))
    destination = Path(output)
    destination.mkdir(parents=True, exist_ok=True)
    source_hash = source_run_hash or hashlib.sha256(
        json.dumps([(chunk.chunk_id, chunk.embedding_text) for chunk in chunks], sort_keys=True).encode()).hexdigest()
    gold_hash = gold_snapshot_hash or _snapshot_hash(cases)
    metadata = {"status": "completed", "source_run_hash": source_hash, "gold_snapshot_hash": gold_hash,
                "provider": provider_name or type(provider).__name__, "indexed_chunks": len(chunks),
                "dimensions": len(next(iter(adapter._vectors.values()))) if chunks else 0}
    summary_path = destination / "retrieval_dense_summary.json"
    details_path = destination / "retrieval_dense_details.jsonl"
    summary_path.write_text(json.dumps({"experiment": "dense", **report.to_dict(), **metadata},
                                       ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    details_path.write_text("".join(json.dumps({**detail, **{key: metadata[key] for key in ("source_run_hash", "gold_snapshot_hash")}},
                                               ensure_ascii=False, sort_keys=True) + "\n" for detail in report.details), encoding="utf-8")
    return DenseBaselineResult(report, summary_path, details_path)


def write_dense_blocked(output: str | Path, error: Exception, *, source_run_hash: str = "",
                        gold_snapshot_hash: str = "") -> Path:
    """Persist an explicit blocked artifact for CLI/reporting integrations."""
    destination = Path(output)
    destination.mkdir(parents=True, exist_ok=True)
    path = destination / "retrieval_dense_summary.json"
    path.write_text(json.dumps({"experiment": "dense", "status": "blocked", "failure": {
        "type": "DENSE_PROVIDER_UNAVAILABLE", "message": str(error)},
        "source_run_hash": source_run_hash, "gold_snapshot_hash": gold_snapshot_hash},
        ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    return path
