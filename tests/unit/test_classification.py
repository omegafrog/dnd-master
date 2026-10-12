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


def test_broad_chapter_prose_does_not_become_spell_or_monster_stat_block():
    classifier = DeterministicContentClassifier()
    chapter = section("Chapter 9: Combat")
    prose = (
        "The spell's range and duration vary by situation. The challenge is to act "
        "quickly; actions taken during combat can change the outcome."
    )

    decision = classifier.classify(chapter, prose)

    assert decision.label is ContentType.UNKNOWN
    assert decision.source_text == prose

    entry_like_prose = "Spell, 3rd-level evocation. Casting Time 1 action. Range 60 feet. Duration 1 minute."
    assert classifier.classify(chapter, entry_like_prose).label is ContentType.UNKNOWN


def test_agent_payload_cannot_replace_source_text():
    decision = structured_decision({"label": "spell", "confidence": 1, "source_text": "tampered"}, "original")
    assert decision.label is ContentType.UNKNOWN
    assert decision.review_required
    assert decision.source_text == "original"


def test_table_words_and_die_notation_in_prose_are_not_table_evidence():
    classifier = DeterministicContentClassifier()

    prose = "Roll a d20 when the table rule tells you to make an ability check."
    table = "d8 Specialty\n1 Officer\n2 Scout\n3 Infantry"

    assert classifier.classify(section("Ability Checks"), prose).label is not ContentType.TABLE
    assert classifier.classify(section("Soldier Specialty"), table).label is ContentType.TABLE


def test_table_embedded_in_long_prose_does_not_classify_the_whole_section_as_table(monkeypatch, caplog):
    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    caplog.set_level("INFO")
    prose = "\n\n".join(f"An item description paragraph explains property {index}." for index in range(30))
    text = f"Item Descriptions\n\n{prose}\n\nd6 Damage Type\n1 Acid\n2 Cold\n3 Fire"

    result = DeterministicContentClassifier().classify(section("Item Descriptions"), text)

    assert result.label is not ContentType.TABLE
    assert "dev_content_classifier stage=table_detection branch=reject_mixed_section" in caplog.text
    assert "expectedMinimumRowRatio=0.5" in caplog.text


def test_magic_item_section_takes_precedence_over_spell_terms_in_its_descriptions():
    text = "Wondrous item, rare (requires attunement). The identify spell reveals its properties."

    result = DeterministicContentClassifier().classify(section("Item Descriptions"), text)

    assert result.label is ContentType.MAGIC_ITEM
