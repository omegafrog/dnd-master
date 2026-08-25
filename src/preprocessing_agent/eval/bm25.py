"""Injected BM25 retrieval adapter and reproducible baseline runner."""

from __future__ import annotations

from dataclasses import dataclass, field
import hashlib
import json
import math
from pathlib import Path
import re
from typing import Iterable, Sequence

from preprocessing_agent.domain import Chunk

from .dense import _snapshot_hash
from .gold import GoldCase
from .retrieval import DEFAULT_CUTOFFS, RankedChunk, RetrievalInputError, RetrievalReport, evaluate_ranked_retrieval


class Bm25InputError(RetrievalInputError):
    """The BM25 index or query cannot satisfy the retrieval input contract."""


_TOKEN = re.compile(r"[\w]+", re.UNICODE)


def tokenize_bm25(value: str) -> tuple[str, ...]:
    """Tokenize normalized embedding text while preserving numeric terms."""
    if not isinstance(value, str):
        raise Bm25InputError("text is required")
    return tuple(_TOKEN.findall(value.casefold()))


@dataclass(slots=True)
class Bm25IndexAdapter:
    """In-memory BM25 index over Chunk.embedding_text with evaluator IDs."""

    k1: float = 1.5
    b: float = 0.75
    _chunks: dict[str, Chunk] = field(init=False, repr=False)
    _terms: dict[str, dict[str, int]] = field(init=False, repr=False)
    _lengths: dict[str, int] = field(init=False, repr=False)
    _average_length: float = field(init=False, repr=False)

    def __post_init__(self) -> None:
        if not math.isfinite(self.k1) or self.k1 < 0:
            raise Bm25InputError("k1 must be a finite non-negative number")
        if not math.isfinite(self.b) or not 0 <= self.b <= 1:
            raise Bm25InputError("b must be between 0 and 1")
        self._chunks = {}
        self._terms = {}
        self._lengths = {}
        self._average_length = 0.0

    def index(self, chunks: Iterable[Chunk]) -> None:
        self._chunks.clear()
        self._terms.clear()
        self._lengths.clear()
        for chunk in chunks:
            if not isinstance(chunk, Chunk) or not chunk.chunk_id:
                raise Bm25InputError("chunk must have an evaluator chunk ID")
            if chunk.chunk_id in self._chunks:
                raise Bm25InputError(f"duplicate chunk ID in BM25 index: {chunk.chunk_id}")
            tokens = tokenize_bm25(chunk.embedding_text)
            self._chunks[chunk.chunk_id] = chunk
            self._lengths[chunk.chunk_id] = len(tokens)
            for term in tokens:
                postings = self._terms.setdefault(term, {})
                postings[chunk.chunk_id] = postings.get(chunk.chunk_id, 0) + 1
        self._average_length = sum(self._lengths.values()) / len(self._lengths) if self._lengths else 0.0

    def retrieve(self, query: str, limit: int = 20) -> tuple[RankedChunk, ...]:
        if not isinstance(query, str) or not query.strip():
            raise Bm25InputError("query is required")
        if limit <= 0 or not self._chunks:
            return ()
        query_terms = tokenize_bm25(query)
        document_count = len(self._chunks)
        scores: dict[str, float] = {chunk_id: 0.0 for chunk_id in self._chunks}
        for term in query_terms:
            postings = self._terms.get(term)
            if not postings:
                continue
            document_frequency = len(postings)
            idf = math.log(1 + (document_count - document_frequency + 0.5) / (document_frequency + 0.5))
            for chunk_id, term_frequency in postings.items():
                length = self._lengths[chunk_id]
                normalization = 1 - self.b + self.b * length / self._average_length if self._average_length else 1.0
                scores[chunk_id] += idf * (term_frequency * (self.k1 + 1)) / (term_frequency + self.k1 * normalization)
        ranked = sorted(scores.items(), key=lambda item: (-item[1], item[0]))[:limit]
        return tuple(RankedChunk(chunk_id, rank, score, {
            "retriever": "bm25", "evaluator_chunk_id": chunk_id,
            "embedding_text_hash": hashlib.sha256(self._chunks[chunk_id].embedding_text.encode("utf-8")).hexdigest(),
        }) for rank, (chunk_id, score) in enumerate(ranked, 1))


@dataclass(frozen=True, slots=True)
class Bm25BaselineResult:
    report: RetrievalReport
    summary_path: Path
    details_path: Path


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


def run_bm25_baseline(
    chunks: Iterable[Chunk], cases: Iterable[GoldCase], output: str | Path,
    *, source_run_hash: str = "", gold_snapshot_hash: str = "", k1: float = 1.5, b: float = 0.75,
) -> Bm25BaselineResult:
    chunks = tuple(chunks)
    cases = tuple(cases)
    adapter = Bm25IndexAdapter(k1=k1, b=b)
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
                "provider": "bm25", "indexed_chunks": len(chunks), "k1": k1, "b": b,
                "tokenization": "casefold_unicode_word_tokens"}
    summary_path = destination / "retrieval_bm25_summary.json"
    details_path = destination / "retrieval_bm25_details.jsonl"
    summary_path.write_text(json.dumps({"experiment": "bm25", **report.to_dict(), **metadata},
                                       ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    details_path.write_text("".join(json.dumps({**detail, **{key: metadata[key] for key in ("source_run_hash", "gold_snapshot_hash")}},
                                               ensure_ascii=False, sort_keys=True) + "\n" for detail in report.details), encoding="utf-8")
    return Bm25BaselineResult(report, summary_path, details_path)


def write_bm25_blocked(output: str | Path, error: Exception, *, source_run_hash: str = "", gold_snapshot_hash: str = "") -> Path:
    destination = Path(output)
    destination.mkdir(parents=True, exist_ok=True)
    path = destination / "retrieval_bm25_summary.json"
    path.write_text(json.dumps({"experiment": "bm25", "status": "blocked", "failure": {
        "type": "BM25_INPUT_ERROR", "message": str(error)},
        "source_run_hash": source_run_hash, "gold_snapshot_hash": gold_snapshot_hash},
        ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    return path
