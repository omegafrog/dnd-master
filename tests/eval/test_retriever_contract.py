import pytest

from preprocessing_agent.eval.retrieval import RetrievalInputError, evaluate_ranked_retrieval


def test_ranked_retrieval_rejects_more_than_top_twenty():
    with pytest.raises(RetrievalInputError, match="invalid ordering"):
        evaluate_ranked_retrieval({"q1": ("c1",)}, {"q1": [f"c{i}" for i in range(21)]})


def test_ranked_retrieval_rejects_unexpected_query():
    with pytest.raises(RetrievalInputError, match="unknown query result"):
        evaluate_ranked_retrieval({"q1": ("c1",)}, {"q1": ["c1"], "q2": ["c1"]})
