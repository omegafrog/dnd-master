import json

import pytest

from preprocessing_agent.domain import Chunk, ContentType
from preprocessing_agent.eval.dense import (
    DenseEmbeddingIndexAdapter,
    DenseProviderUnavailable,
    run_dense_baseline,
)
from preprocessing_agent.eval.gold import GoldCase


class FixtureEmbeddingProvider:
    dimensions = 2

    def embed(self, text):
        return {"query": (1.0, 0.0), "indexed text": (1.0, 0.0), "other": (0.0, 1.0)}[text]


def _chunk(chunk_id, embedding_text):
    return Chunk(chunk_id, chunk_id, ContentType.RULE, "ignored source", embedding_text, 1, ())


def test_dense_adapter_indexes_embedding_text_and_returns_evaluator_ids():
    adapter = DenseEmbeddingIndexAdapter(FixtureEmbeddingProvider())
    adapter.index((_chunk("chunk-1", "indexed text"), _chunk("chunk-2", "other")))

    ranked = adapter.retrieve("query", 2)

    assert [item.chunk_id for item in ranked] == ["chunk-1", "chunk-2"]
    assert ranked[0].rank == 1


def test_dense_provider_absence_is_explicit():
    with pytest.raises(DenseProviderUnavailable, match="embedding provider"):
        DenseEmbeddingIndexAdapter(None)


def test_dense_runner_writes_metrics_and_snapshot_metadata(tmp_path):
    result = run_dense_baseline(
        (_chunk("chunk-1", "indexed text"), _chunk("chunk-2", "other")),
        (GoldCase("case-1", "query", gold_chunk_ids=("chunk-1",)),),
        FixtureEmbeddingProvider(),
        tmp_path,
        source_run_hash="source-hash",
        gold_snapshot_hash="gold-hash",
    )

    summary = json.loads(result.summary_path.read_text())
    assert summary["status"] == "completed"
    assert summary["source_run_hash"] == "source-hash"
    assert summary["gold_snapshot_hash"] == "gold-hash"
    assert summary["recall_at_1"] == 1.0
    assert summary["mrr"] == 1.0
    assert summary["evidence_recall"] == 1.0
    assert json.loads(result.details_path.read_text())["ranked_chunk_ids"] == ["chunk-1", "chunk-2"]
