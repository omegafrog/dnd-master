import json

from preprocessing_agent.eval.retrieval import evaluate_ranked_retrieval, write_retrieval_artifacts


def test_retrieval_artifacts_write_summary_and_details_jsonl(tmp_path):
    report = evaluate_ranked_retrieval(
        {"q1": ("c1",)}, {"q1": ["noise", "c1"]}, known_chunk_ids={"noise", "c1"},
    )
    summary, details = write_retrieval_artifacts(report, tmp_path, "offline")
    assert summary.name == "retrieval_offline_summary.json"
    assert details.name == "retrieval_offline_details.jsonl"
    assert json.loads(summary.read_text())["cutoffs"] == [1, 3, 5, 10, 20]
    assert json.loads(details.read_text()) ["first_gold_rank"] == 2
