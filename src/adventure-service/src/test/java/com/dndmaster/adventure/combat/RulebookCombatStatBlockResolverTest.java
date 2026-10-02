package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.runtime.CombatEnemyProposal;
import com.dndmaster.adventure.application.runtime.RulebookCombatStatBlockResolver;
import com.dndmaster.adventure.application.runtime.RuntimeEvidence;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceType;
import com.dndmaster.adventure.domain.combat.CombatEnemyStatBlock;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RulebookCombatStatBlockResolverTest {
    @Test
    void materializes_combat_stats_only_from_a_rulebook_stat_block() {
        UUID documentId = UUID.randomUUID();
        RuntimeEvidence evidence = new RuntimeEvidence(RuntimeEvidenceType.RULEBOOK,
                new KnowledgeDocumentId(documentId), 3, "page-135",
                "Giant Rat\nArmor Class 12 Hit Points 7 (2d6) Speed 30 ft.\n"
                        + "Bite. Melee Weapon Attack: +4 to hit, reach 5 ft., one target. "
                        + "Hit: 4 (1d6 + 2) piercing damage.");

        var result = RulebookCombatStatBlockResolver.resolve(
                new CombatEnemyProposal("instant-rat", "giant-rat", "Giant Rat", 1,
                        com.dndmaster.adventure.application.runtime.CombatStartMode.INSTANT),
                List.of(evidence));

        assertTrue(result.isPresent());
        CombatEnemyStatBlock stats = result.orElseThrow();
        assertEquals(12, stats.armorClass());
        assertEquals(7, stats.hitPointMaximum());
        assertEquals(4, stats.attackModifier());
        assertEquals("1d6 + 2", stats.damageDice());
        assertEquals(documentId, stats.source().knowledgeDocumentId());
    }

    @Test
    void resolves_plural_scenario_name_from_singular_rulebook_monster_entry() {
        UUID documentId = UUID.randomUUID();
        RuntimeEvidence evidence = new RuntimeEvidence(RuntimeEvidenceType.RULEBOOK,
                new KnowledgeDocumentId(documentId), 3, "page=123:chunk=giant-rat",
                "거대 쥐 Giant Rat\n방어도 12\n히트 포인트 7 (2d6)\n"
                        + "물기. 근접 무기 공격: 명중 +4, 간격 5ft, 목표 하나. 명중시: 4(1d4+2) 점의 관통 피해.");

        var result = RulebookCombatStatBlockResolver.resolve(
                new CombatEnemyProposal("encounter-beer-cellar-giant-rats", "giant-rats", "Giant Rats", 8,
                        com.dndmaster.adventure.application.runtime.CombatStartMode.SCENARIO),
                List.of(evidence));

        assertTrue(result.isPresent());
        CombatEnemyStatBlock stats = result.orElseThrow();
        assertEquals(12, stats.armorClass());
        assertEquals(7, stats.hitPointMaximum());
        assertEquals(4, stats.attackModifier());
        assertEquals("1d4+2", stats.damageDice());
        assertEquals(documentId, stats.source().knowledgeDocumentId());
    }

    @Test
    void does_not_use_an_unrelated_storybook_stat_block_that_only_mentions_the_monster_later() {
        UUID storyDocumentId = UUID.randomUUID();
        UUID rulebookDocumentId = UUID.randomUUID();
        RuntimeEvidence unrelatedStoryStats = new RuntimeEvidence(RuntimeEvidenceType.STORYBOOK,
                new KnowledgeDocumentId(storyDocumentId), 2, "page=4:chunk=spider-and-rat",
                "Giant Inferno Spider\nArmor Class 14 (natural armor)\nHit Points 32 (5d10 + 5)\n"
                        + "Flaming Bite: Melee Weapon Attack: +5 to hit. Hit: 6 (1d8 + 2).\n"
                        + "Later, a small black rat transforms into a Giant Rat.");
        RuntimeEvidence ratRules = new RuntimeEvidence(RuntimeEvidenceType.RULEBOOK,
                new KnowledgeDocumentId(rulebookDocumentId), 3, "page=123:chunk=giant-rat",
                "거대 쥐 Giant Rat\n방어도 12\n히트 포인트 7 (2d6)\n"
                        + "물기. 근접 무기 공격: 명중 +4, 간격 5ft, 목표 하나. 명중시: 4(1d4+2) 점의 관통 피해.");

        var result = RulebookCombatStatBlockResolver.resolve(
                new CombatEnemyProposal("encounter-beer-cellar-giant-rats", "giant-rats", "Giant Rats", 8,
                        com.dndmaster.adventure.application.runtime.CombatStartMode.SCENARIO),
                List.of(unrelatedStoryStats, ratRules));

        assertTrue(result.isPresent());
        CombatEnemyStatBlock stats = result.orElseThrow();
        assertEquals(12, stats.armorClass());
        assertEquals(7, stats.hitPointMaximum());
        assertEquals(4, stats.attackModifier());
        assertEquals("1d4+2", stats.damageDice());
        assertEquals(rulebookDocumentId, stats.source().knowledgeDocumentId());
    }

    @Test
    void resolves_combat_numbers_from_a_structured_storybook_creature_entry() {
        UUID documentId = UUID.randomUUID();
        var result = RulebookCombatStatBlockResolver.resolve(
                new CombatEnemyProposal("instant-spider", "giant-inferno-spider", "Giant Inferno Spider", 1,
                        com.dndmaster.adventure.application.runtime.CombatStartMode.INSTANT),
                List.of(new RuntimeEvidence(RuntimeEvidenceType.STORYBOOK,
                        new KnowledgeDocumentId(documentId), 2, "page=4:creature",
                        "Giant Inferno Spider Armor Class 14 Hit Points 32 (5d10 + 5). "
                                + "Flaming Bite: Melee Weapon Attack: +5 to hit. Hit: 6 (1d8 + 2) piercing damage.")));

        assertTrue(result.isPresent());
        assertEquals(14, result.orElseThrow().armorClass());
        assertEquals(32, result.orElseThrow().hitPointMaximum());
        assertEquals(5, result.orElseThrow().attackModifier());
        assertEquals("1d8 + 2", result.orElseThrow().damageDice());
        assertEquals(documentId, result.orElseThrow().source().knowledgeDocumentId());
    }
}
