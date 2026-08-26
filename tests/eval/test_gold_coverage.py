from preprocessing_agent.domain import Chunk, ContentType
from preprocessing_agent.eval.gold import GoldCase, RequiredEvidence, evaluate_gold


def test_gold_metrics_map_keys_and_measure_evidence_completeness():
    chunks = (
        Chunk("c1", "spell.fireball", ContentType.SPELL, "Fireball", "Fireball", 1, ()),
        Chunk("c2", "spell.fireball.damage", ContentType.SPELL, "Damage", "Damage", 1, ()),
    )
    cases = (GoldCase("q1", "How does Fireball work?", ("spell.fireball",), (RequiredEvidence("identity", ("spell.fireball",)),)),
             GoldCase("q2", "What is missing?", ("spell.missing",), ()))
    result = evaluate_gold(cases, chunks)
    assert result.metrics["gold_context_coverage"] == .5
    assert result.metrics["single_chunk_answerability_rate"] == .5
    assert result.metrics["evidence_completeness"] == 1.0
    assert result.unmatched_keys == ("spell.missing",)
