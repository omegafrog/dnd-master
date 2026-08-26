from preprocessing_agent.eval import evaluate_retrieval
def test_retrieval_metrics_include_recall_mrr_and_evidence_recall():
    report = evaluate_retrieval({"q1": {"gold-1"}}, {"q1": ["gold-2", "gold-1"]})
    assert report.recall_at[1] == 0.0
    assert report.recall_at[3] == 1.0
    assert report.mrr == 0.5
    assert report.evidence_recall == 1.0
