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
    void ignores_storybook_text_when_resolving_rulebook_stats() {
        var result = RulebookCombatStatBlockResolver.resolve(
                new CombatEnemyProposal("instant-rat", "giant-rat", "Giant Rat", 1,
                        com.dndmaster.adventure.application.runtime.CombatStartMode.INSTANT),
                List.of(new RuntimeEvidence(RuntimeEvidenceType.STORYBOOK,
                        new KnowledgeDocumentId(UUID.randomUUID()), 1, "page-2",
                        "Eight Giant Rats begin combat.")));

        assertTrue(result.isEmpty());
    }
}
