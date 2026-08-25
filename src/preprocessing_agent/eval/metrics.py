from __future__ import annotations
from dataclasses import dataclass
import hashlib
import re
from statistics import mean, median
from typing import Iterable, Mapping, Sequence

from preprocessing_agent.domain import Chunk, ContentType
from preprocessing_agent.utils.tokens import tokenize

_SPACE = re.compile(r"\s+")
_PUNCT = re.compile(r"[^\w\s]", re.UNICODE)
_PROSE = {ContentType.NARRATIVE, ContentType.RULE, ContentType.BACKGROUND, ContentType.UNKNOWN}

def normalized_text(value: str) -> str:
    return _SPACE.sub(" ", _PUNCT.sub(" ", value.casefold())).strip()

def normalized_hash(value: str) -> str:
    return hashlib.sha256(normalized_text(value).encode("utf-8")).hexdigest()

def token_ngrams(value: str, size: int = 5) -> set[tuple[str, ...]]:
    words = [word.casefold() for word in tokenize(value)]
    return {tuple(words[index:index + size]) for index in range(len(words) - size + 1)}

def jaccard(left: set[tuple[str, ...]], right: set[tuple[str, ...]]) -> float:
    if not left and not right:
        return 0.0
    return len(left & right) / len(left | right)

def token_statistics(chunks: Sequence[Chunk], tiny_threshold: int, oversized_threshold: int) -> dict[str, float | int]:
    values = [max(0, int(chunk.token_count)) for chunk in chunks]
    if not values:
        return {"count": 0, "mean": 0.0, "median": 0.0, "p90": 0.0, "p95": 0.0, "tiny_rate": 0.0, "oversized_rate": 0.0}
    ordered = sorted(values)
    def percentile(percent: float) -> float:
        position = (len(ordered) - 1) * percent
        low, high = int(position), min(int(position) + 1, len(ordered) - 1)
        return ordered[low] + (ordered[high] - ordered[low]) * (position - low)
    return {"count": len(values), "mean": mean(values), "median": median(values), "p90": percentile(.90), "p95": percentile(.95),
            "tiny_rate": sum(value < tiny_threshold for value in values) / len(values), "oversized_rate": sum(value > oversized_threshold for value in values) / len(values)}

def broken_boundary(chunk: Chunk) -> bool:
    if chunk.content_type not in _PROSE:
        return False
    text = chunk.source_text.strip()
    return not text or text[0].islower() or text.endswith((",", ";", ":", "—", "-")) or text[-1].isalnum()

def boundary_metrics(chunks: Sequence[Chunk]) -> dict[str, float | int]:
    prose = [chunk for chunk in chunks if chunk.content_type in _PROSE]
    broken = sum(broken_boundary(chunk) for chunk in prose)
    return {"prose_chunks": len(prose), "broken_prose_chunks": broken, "broken_boundary_rate": broken / len(prose) if prose else 0.0, "non_prose_exempt": len(chunks) - len(prose)}

def duplicate_metrics(chunks: Sequence[Chunk], near_threshold: float = .8) -> dict[str, object]:
    exact_groups: dict[str, list[str]] = {}
    for chunk in chunks:
        exact_groups.setdefault(normalized_hash(chunk.source_text), []).append(chunk.chunk_id)
    exact_groups = {key: sorted(ids) for key, ids in exact_groups.items() if len(ids) > 1}
    near_pairs: list[dict[str, object]] = []
    grams = {chunk.chunk_id: token_ngrams(chunk.source_text) for chunk in chunks}
    for index, left in enumerate(chunks):
        for right in chunks[index + 1:]:
            score = jaccard(grams[left.chunk_id], grams[right.chunk_id])
            if score >= near_threshold and left.chunk_id != right.chunk_id:
                near_pairs.append({"chunk_ids": [left.chunk_id, right.chunk_id], "jaccard": score})
    exact_ids = {item for group in exact_groups.values() for item in group}
    near_ids = {item for pair in near_pairs for item in pair["chunk_ids"]}
    count = len(chunks) or 1
    return {"exact_groups": [{"normalized_hash": key, "chunk_ids": ids} for key, ids in sorted(exact_groups.items())], "exact_duplicate_rate": len(exact_ids) / count,
            "near_duplicate_pairs": sorted(near_pairs, key=lambda item: (item["chunk_ids"], item["jaccard"])), "near_duplicate_rate": len(near_ids) / count}
@dataclass(frozen=True, slots=True)
class EvaluationReport:
    recall_at: Mapping[int, float]
    mrr: float
    evidence_recall: float
    queries: int
    def to_dict(self) -> dict[str, object]:
        return {"recall_at": {str(k): v for k, v in self.recall_at.items()}, "mrr": self.mrr, "evidence_recall": self.evidence_recall, "queries": self.queries}
def evaluate_retrieval(gold: Mapping[str, Iterable[str]], retrieved: Mapping[str, Iterable[str]], ks: tuple[int, ...] = (1, 3, 5, 10)) -> EvaluationReport:
    gold_sets = {query: set(values) for query, values in gold.items()}
    scores = {k: 0.0 for k in ks}
    reciprocal, evidence = 0.0, 0.0
    for query, expected in gold_sets.items():
        actual = list(retrieved.get(query, ()))
        if expected and expected.intersection(actual):
            evidence += 1.0
            reciprocal += 1.0 / (next(i for i, item in enumerate(actual, 1) if item in expected))
        for k in ks:
            if expected.intersection(actual[:k]):
                scores[k] += 1.0
    count = len(gold_sets) or 1
    return EvaluationReport({k: value / count for k, value in scores.items()}, reciprocal / count, evidence / count, len(gold_sets))
