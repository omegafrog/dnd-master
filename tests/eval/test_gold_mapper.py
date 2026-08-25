from preprocessing_agent.eval import GoldContextMapper
from preprocessing_agent.domain import Chunk, ContentType
def test_gold_context_keys_map_to_stable_chunk_ids():
    chunks = (Chunk("chk-fire", "ch09.combat.fire_bolt", ContentType.SPELL, "Fire Bolt", "Fire Bolt", 2, ()),)
    mapped = GoldContextMapper().map(("ch09.combat.fire_bolt",), chunks)
    assert mapped.keys == ("chk-fire",)
    assert mapped.unmatched == ()
