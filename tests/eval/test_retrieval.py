import pytest

from preprocessing_agent.eval.retrieval import (
    RankedChunk,
    OfflineRankedIdRetriever,
    RetrievalInputError,
    evaluate_ranked_retrieval,
)


def test_retrieval_scores_cutoffs_mrr_and_single_multi_evidence():
    report = evaluate_ranked_retrieval(
        {"single": ("c1",), "multi": ("c2", "c3")},
        {"single": ["other", "c1"], "multi": ["c2", "other", "c3"]},
    )

    assert report.recall_at == {1: .5, 3: 1.0, 5: 1.0, 10: 1.0, 20: 1.0}
    assert report.mrr == .75
    assert report.evidence_recall_at_5 == 1.0
    assert (report.single_evidence_queries, report.multi_evidence_queries) == (1, 1)


@pytest.mark.parametrize(
    ("gold", "retrieved", "message"),
    [
        ({"q1": ("c1",)}, {}, "missing query result"),
        ({"q1": ("c1",)}, {"q1": ["c1", "c1"]}, "duplicate result"),
        ({"q1": ("c1",)}, {"q1": {"c1": 1}}, "invalid ordering"),
        ({"q1": ("c1",)}, {"q1": ["unknown"]}, "unknown chunk ID"),
    ],
)
def test_retrieval_input_contract_failures_are_explicit(gold, retrieved, message):
    with pytest.raises(RetrievalInputError, match=message):
        evaluate_ranked_retrieval(gold, retrieved, known_chunk_ids={"c1"})


def test_offline_adapter_preserves_ranked_ids_and_limit():
    adapter = OfflineRankedIdRetriever({"q1": ["c1", "c2", "c3"]})
    assert adapter.retrieve("q1", 2) == ("c1", "c2")
    with pytest.raises(RetrievalInputError, match="missing query result"):
        adapter.retrieve("missing")


def test_ranked_chunk_contract_supports_rank_score_and_metadata():
    result = RankedChunk(chunk_id="c1", rank=1, score=0.9, metadata={"source": "fixture"})
    assert result.chunk_id == "c1"
    assert result.rank == 1
    assert result.score == .9
    assert result.metadata["source"] == "fixture"


def test_retrieval_supports_top_twenty_and_evidence_recall_at_each_cutoff():
    ranked = [f"noise-{index}" for index in range(18)] + ["c1", "c2"]
    report = evaluate_ranked_retrieval(
        {"q1": ("c1", "c2")}, {"q1": ranked},
        required_evidence={"q1": ("c1", "c2")}, known_chunk_ids=set(ranked),
    )
    assert report.recall_at[20] == 1.0
    assert report.evidence_recall_at[5] == 0.0
    assert report.evidence_recall_at[20] == 1.0
    assert report.details[0]["first_gold_rank"] == 19
    assert report.details[0]["evidence_completeness"] == 1.0
