from preprocessing_agent.domain import Chunk, ContentType
from preprocessing_agent.eval.semantic import (
    EntityFixture,
    SemanticCandidateDetector,
    SemanticDecision,
    decide_candidates,
)


def chunk(chunk_id, key, text, content_type=ContentType.SPELL, parent_key=None):
    return Chunk(chunk_id, key, content_type, text, text, len(text.split()), (), (key,), parent_key)


def test_atomic_entity_split_and_mixed_context_candidates_are_deterministic():
    chunks = (
        chunk("c1", "spell.fireball.part-1", "Fireball casting time and range."),
        chunk("c2", "spell.fireball.part-2", "Fireball damage and saving throw."),
        chunk("c3", "mixed", "A spell heading then a goblin stat block", ContentType.MONSTER_STAT_BLOCK),
    )
    fixtures = (EntityFixture("fireball", "spell.fireball", ContentType.SPELL, True),)
    candidates = SemanticCandidateDetector(large_span_tokens=5).detect(chunks, fixtures)
    assert [candidate.issue for candidate in candidates] == ["SPLIT_ENTITY", "MIXED_CONTEXT"]
    assert candidates[0].chunk_ids == ("c1", "c2")
    assert candidates[1].chunk_ids == ("c3",)


def test_optional_judge_only_returns_valid_issue_action():
    decision = decide_candidates([], lambda candidate: SemanticDecision(False, "MIXED_CONTEXT", "split"))
    assert decision == []

