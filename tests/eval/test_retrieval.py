import pytest

from preprocessing_agent.eval.retrieval import (
    OfflineRankedIdRetriever,
    RetrievalInputError,
    evaluate_ranked_retrieval,
)


def test_retrieval_scores_cutoffs_mrr_and_single_multi_evidence():
    report = evaluate_ranked_retrieval(
        {"single": ("c1",), "multi": ("c2", "c3")},
        {"single": ["other", "c1"], "multi": ["c2", "other", "c3"]},
    )

    assert report.recall_at == {1: .5, 3: 1.0, 5: 1.0, 10: 1.0}
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
