import json
from pathlib import Path

import pytest

from preprocessing_agent.domain import Chunk, ContentType
from preprocessing_agent.eval.bm25 import (
    Bm25IndexAdapter,
    Bm25InputError,
    run_bm25_baseline,
    tokenize_bm25,
)
from preprocessing_agent.eval.gold import GoldCase


def _chunk(chunk_id, embedding_text):
    return Chunk(chunk_id, chunk_id, ContentType.RULE, "source", embedding_text, 1, ())


def _contract():
    return json.loads((Path(__file__).parents[1] / "fixtures" / "hybrid-retrieval-deterministic-contract.json").read_text())


def test_bm25_matches_shared_deterministic_contract():
    contract = _contract()
    bm25 = contract["bm25"]
    adapter = Bm25IndexAdapter(k1=bm25["k1"], b=bm25["b"])
    adapter.index(tuple(_chunk(document["id"], document["text"]) for document in bm25["documents"]))

    assert tokenize_bm25(contract["tokenizer"]["input"]) == tuple(contract["tokenizer"]["expected_terms"])
    ranked = adapter.retrieve(bm25["query"], len(bm25["documents"]))
    assert [item.chunk_id for item in ranked] == bm25["expected_order"]
    assert {item.chunk_id: item.score for item in ranked} == pytest.approx(bm25["expected_scores"])


def test_bm25_tokenizes_normalized_text_and_ranks_exact_numeric_terms():
    adapter = Bm25IndexAdapter()
    adapter.index((_chunk("name", "Fireball spell damage"),
                   _chunk("number", "Fireball damage 8d6"),
                   _chunk("other", "Healing potion")))

    assert tokenize_bm25("Fireball, 8d6!") == ("fireball", "8d6")
    ranked = adapter.retrieve("What is Fireball 8d6 damage?", 3)
    assert ranked[0].chunk_id == "number"
    assert ranked[0].metadata["evaluator_chunk_id"] == "number"
    assert ranked[0].metadata["retriever"] == "bm25"


def test_bm25_empty_index_and_invalid_inputs_are_explicit():
    adapter = Bm25IndexAdapter()
    assert adapter.retrieve("missing") == ()
    with pytest.raises(Bm25InputError, match="duplicate chunk ID"):
        adapter.index((_chunk("same", "one"), _chunk("same", "two")))
    with pytest.raises(Bm25InputError, match="query is required"):
        adapter.retrieve(" ")


def test_bm25_runner_writes_shared_metrics_and_provenance(tmp_path):
    result = run_bm25_baseline(
        (_chunk("chunk-1", "Fireball 8d6 damage"), _chunk("chunk-2", "Healing potion")),
        (GoldCase("case-1", "What is Fireball 8d6 damage?", gold_chunk_ids=("chunk-1",)),),
        tmp_path,
        source_run_hash="source-hash",
        gold_snapshot_hash="gold-hash",
    )

    summary = json.loads(result.summary_path.read_text())
    detail = json.loads(result.details_path.read_text())
    assert summary["status"] == "completed"
    assert summary["source_run_hash"] == "source-hash"
    assert summary["gold_snapshot_hash"] == "gold-hash"
    assert summary["cutoffs"] == [1, 3, 5, 10, 20]
    assert summary["recall_at_1"] == 1.0
    assert summary["mrr"] == 1.0
    assert summary["evidence_recall"] == 1.0
    assert detail["ranked_chunk_ids"][0] == "chunk-1"
    assert detail["source_run_hash"] == "source-hash"
