from preprocessing_agent.pipeline.retry import PageRetryPolicy
import pytest


def test_retry_policy_maps_findings_and_caps_two_additional_attempts():
    policy = PageRetryPolicy()
    page = {"page_number": 4, "attempts": 1, "findings": ["AMBIGUOUS_COLUMNS"],
            "layout_review": {"profiles": [{"candidates": [{"score": .7}, {"score": .82}]}]}}
    attempt = policy.request(page, regions=[(1, 2, 3, 4)])
    assert attempt.attempt_number == 2
    assert attempt.strategy == "alternate-column-hypothesis"
    assert attempt.regions == ((1.0, 2.0, 3.0, 4.0),)
    assert policy.can_retry(2)
    assert not policy.can_retry(3)
    with pytest.raises(ValueError, match="RETRY_BUDGET_EXHAUSTED"):
        policy.request({**page, "attempts": 3})


def test_alternate_column_strategy_requires_a_distinguishable_candidate():
    policy = PageRetryPolicy()
    profile = {"candidates": [{"score": .73}, {"score": .84}], "selected": 0}
    page = {"page_number": 1, "findings": ["AMBIGUOUS_COLUMNS"],
            "layout_review": {"profiles": [profile]}}
    assert policy.directive(page).strategy == "alternate-column-hypothesis"
    unresolved = {**page, "layout_review": {"profiles": [
        {"candidates": [{"score": .73}, {"score": .75}], "selected": 0}]}}
    assert policy.directive(unresolved).strategy == "diagnostic-recheck"
