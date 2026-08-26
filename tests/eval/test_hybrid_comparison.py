import json

import pytest

from preprocessing_agent.domain import Chunk, ContentType
from preprocessing_agent.eval.gold import GoldCase
from preprocessing_agent.eval.hybrid import (
    FAILURE_TAXONOMY,
    RrfHybridRetriever,
    classify_retrieval_failure,
    compare_retrieval_experiments,
    run_hybrid_baseline,
)
from preprocessing_agent.eval.retrieval import RankedChunk, RetrievalInputError


def _ranked(*ids):
    return tuple(RankedChunk(chunk_id=chunk_id, rank=index, score=1.0 / index)
                 for index, chunk_id in enumerate(ids, 1))


class FixtureRetriever:
    def __init__(self, values):
        self.values = values

    def retrieve(self, query, limit=20):
        return self.values[query][:limit]


def test_rrf_uses_reciprocal_rank_and_deterministic_ties():
    hybrid = RrfHybridRetriever(
        FixtureRetriever({"q": _ranked("shared", "dense-only")}),
        FixtureRetriever({"q": _ranked("shared", "bm25-only")}),
        rrf_k=60,
    )

    result = hybrid.retrieve("q", 3)

    assert [item.chunk_id for item in result] == ["shared", "bm25-only", "dense-only"]
    assert result[0].score == pytest.approx(2 / 61)
    assert result[0].metadata["retriever"] == "hybrid_rrf"
    assert result[0].metadata["sources"] == ["bm25", "dense"]


def test_failure_taxonomy_has_deterministic_priority_and_complete_values():
    assert set(FAILURE_TAXONOMY) == {
        "RETRIEVAL_MISS", "RANKING_ERROR", "CHUNK_BOUNDARY", "QUERY_MISMATCH",
        "METADATA_MISMATCH", "MULTI_EVIDENCE_MISS", "TABLE_RETRIEVAL_FAILURE",
    }
    assert classify_retrieval_failure(
        query="what is the table value", ranked_ids=(), gold_ids=("c1",),
        evidence_ids=("c1",), metadata={"content_type": "table"},
    ) == "TABLE_RETRIEVAL_FAILURE"
    assert classify_retrieval_failure(
        query="what is c1", ranked_ids=("other",), gold_ids=("c1",),
        evidence_ids=("c1",), metadata={},
    ) == "RETRIEVAL_MISS"
    assert classify_retrieval_failure(
        query="what", ranked_ids=("c1",), gold_ids=("c1",),
        evidence_ids=("c1", "c2"), metadata={"multi_evidence": True},
    ) == "MULTI_EVIDENCE_MISS"


def test_hybrid_runner_writes_same_snapshot_comparison_and_failures(tmp_path):
    chunks = (
        Chunk("c1", "rule.one", ContentType.RULE, "one", "one", 1, ()),
        Chunk("c2", "rule.two", ContentType.RULE, "two", "two", 1, ()),
    )
    cases = (GoldCase("q1", "one", gold_chunk_ids=("c1",)), GoldCase("q2", "two", gold_chunk_ids=("c2",)))
    dense = FixtureRetriever({"one": _ranked("c2", "c1"), "two": _ranked("c2", "c1")})
    bm25 = FixtureRetriever({"one": _ranked("c1", "c2"), "two": _ranked("c1", "c2")})

    result = run_hybrid_baseline(chunks, cases, dense, bm25, tmp_path,
                                 source_run_hash="source", gold_snapshot_hash="gold")

    summary = json.loads(result.summary_path.read_text())
    failures = result.failure_path.read_text().splitlines()
    assert summary["status"] == "completed"
    assert summary["source_run_hash"] == "source"
    assert summary["gold_snapshot_hash"] == "gold"
    assert summary["cutoffs"] == [1, 3, 5, 10, 20]
    assert summary["recall_at_5"] == 1.0
    assert len(failures) == 0

    comparison = compare_retrieval_experiments(
        {"experiment": "dense", "status": "completed", "source_run_hash": "source", "gold_snapshot_hash": "gold", "cutoffs": [1, 3, 5, 10, 20], "recall_at_5": .5, "mrr": .5, "evidence_recall": .5},
        {"experiment": "bm25", "status": "completed", "source_run_hash": "source", "gold_snapshot_hash": "gold", "cutoffs": [1, 3, 5, 10, 20], "recall_at_5": .5, "mrr": .5, "evidence_recall": .5},
        summary,
    )
    assert comparison["experiments"]["hybrid"]["recall_at_5"] == 1.0
    assert comparison["snapshot"]["source_run_hash"] == "source"


def test_comparison_rejects_mismatched_snapshots_without_partial_success():
    base = {"experiment": "dense", "status": "completed", "source_run_hash": "source", "gold_snapshot_hash": "gold", "cutoffs": [1, 3, 5, 10, 20]}
    with pytest.raises(RetrievalInputError, match="snapshot"):
        compare_retrieval_experiments(base, {**base, "experiment": "bm25", "source_run_hash": "other"}, base)


def test_cli_keeps_valid_experiments_when_one_artifact_is_malformed(tmp_path):
    valid = {"status": "completed", "source_run_hash": "source", "gold_snapshot_hash": "gold", "cutoffs": [1, 3, 5, 10, 20]}
    dense = tmp_path / "dense.json"
    dense.write_text(json.dumps({**valid, "recall_at_5": .5}), encoding="utf-8")
    hybrid = tmp_path / "hybrid.json"
    hybrid.write_text(json.dumps({**valid, "recall_at_5": .8}), encoding="utf-8")
    malformed = tmp_path / "bm25.json"
    malformed.write_text("[]", encoding="utf-8")
    output = tmp_path / "comparison.json"
    import subprocess
    import sys
    result = subprocess.run([sys.executable, "scripts/compare_retrieval.py", str(dense), str(malformed), str(hybrid), "--output", str(output)], capture_output=True, text=True)
    assert result.returncode == 0, result.stderr
    value = json.loads(output.read_text())
    assert value["status"] == "partial"
    assert set(value["experiments"]) == {"dense", "hybrid"}
    assert "bm25" in value["errors"]
