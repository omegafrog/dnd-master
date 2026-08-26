from preprocessing_agent.domain import Chunk, ContentType, SourceSpan
from preprocessing_agent.eval.metrics import boundary_metrics, duplicate_metrics, token_statistics


def chunk(chunk_id, text, content_type=ContentType.NARRATIVE, tokens=None):
    return Chunk(chunk_id, chunk_id, content_type, text, text, tokens if tokens is not None else len(text.split()), (SourceSpan(1, 0, 0, len(text)),))


def test_size_percentiles_and_rates_are_deterministic():
    result = token_statistics([chunk("a", "a", tokens=1), chunk("b", "a b", tokens=2), chunk("c", "a b c", tokens=3)], 2, 2)
    assert result["mean"] == 2
    assert result["median"] == 2
    assert result["p90"] == 2.8
    assert result["tiny_rate"] == 1 / 3
    assert result["oversized_rate"] == 1 / 3


def test_non_prose_is_exempt_from_prose_boundary_rule_and_duplicates_use_hash_and_5grams():
    values = [chunk("a", "alpha beta gamma delta epsilon."), chunk("b", "Alpha, beta gamma delta epsilon"), chunk("c", "table", ContentType.TABLE)]
    boundaries = boundary_metrics(values)
    assert boundaries["non_prose_exempt"] == 1
    duplicates = duplicate_metrics(values, .7)
    assert duplicates["exact_duplicate_rate"] == 2 / 3
    assert duplicates["near_duplicate_pairs"] == []
