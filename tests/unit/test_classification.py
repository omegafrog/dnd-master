from preprocessing_agent.classification import DeterministicContentClassifier
from preprocessing_agent.classification.agent import structured_decision
from preprocessing_agent.domain import ContentType, SectionNode


def section(title="Fireball"):
    return SectionNode("s1", title, 1, ContentType.NARRATIVE)


def test_dnd_patterns_are_deterministic_and_low_confidence_is_review():
    classifier = DeterministicContentClassifier()
    assert classifier.classify(section(), "Spell, 3rd-level evocation. Casting Time 1 action. Range 150 feet.").label is ContentType.SPELL
    assert classifier.classify(section("Goblin"), "Armor Class 15; Hit Points 7; Challenge 1/4; Actions").label is ContentType.MONSTER_STAT_BLOCK
    assert classifier.classify(section("Unknown"), "ordinary prose").review_required


def test_agent_payload_cannot_replace_source_text():
    decision = structured_decision({"label": "spell", "confidence": 1, "source_text": "tampered"}, "original")
    assert decision.label is ContentType.UNKNOWN
    assert decision.review_required
    assert decision.source_text == "original"
