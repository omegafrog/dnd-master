from pathlib import Path

from preprocessing_agent.chunking import ChunkPolicy, load_policy
from preprocessing_agent.domain import ContentType


def test_dnd_policy_has_contract_limits_and_strategies():
    policy = load_policy(Path("configs/profiles/dnd_rulebook.yaml"))
    assert (policy.target_tokens, policy.max_tokens, policy.min_tokens, policy.overlap_tokens) == (350, 500, 100, 30)
    assert policy.strategy_for(ContentType.SPELL) == "atomic"
    assert policy.strategy_for(ContentType.TABLE) == "table"
    assert policy.strategy_for(ContentType.RULE) == "rule"


def test_policy_rejects_invalid_limits():
    try:
        ChunkPolicy(target_tokens=600)
    except ValueError:
        pass
    else:
        raise AssertionError("invalid limits must be rejected")
