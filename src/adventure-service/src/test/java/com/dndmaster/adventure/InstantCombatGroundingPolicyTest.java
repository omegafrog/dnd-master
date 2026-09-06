package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.runtime.CombatEnemyProposal;
import com.dndmaster.adventure.application.runtime.CombatScenarioGroundingPolicy;
import com.dndmaster.adventure.application.runtime.CombatStartMode;
import com.dndmaster.adventure.application.runtime.RuntimeEvidence;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceType;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.runtime.CurrentSituation;
import com.dndmaster.adventure.domain.scenario.ScenarioModel;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InstantCombatGroundingPolicyTest {
    @Test
    void permits_gm_forced_instant_combat_when_story_and_rulebook_evidence_are_present() {
        RuntimeEvidence story = evidence(RuntimeEvidenceType.STORYBOOK, "page-2",
                "The noise echoes through the cellar and something moves in the dark.");
        RuntimeEvidence rules = evidence(RuntimeEvidenceType.RULEBOOK, "page-135",
                "Giant Rat Armor Class 12 Hit Points 7 (2d6) "
                        + "Bite. Melee Weapon Attack: +4 to hit. Hit: 4 (1d6 + 2) piercing damage.");

        var grounded = CombatScenarioGroundingPolicy.ground(ScenarioModel.empty(),
                CurrentSituation.initial("cellar"),
                List.of(new CombatEnemyProposal("", "giant-rat", "Giant Rat", 1, CombatStartMode.INSTANT)),
                List.of(story), List.of(rules));

        assertEquals(CombatStartMode.INSTANT, grounded.get(0).mode());
        assertEquals("giant-rat", grounded.get(0).enemyKey());
        assertEquals(12, grounded.get(0).statBlock().armorClass());
        org.junit.jupiter.api.Assertions.assertTrue(grounded.get(0).scenarioId().startsWith("instant-"));
    }

    @Test
    void rejects_instant_combat_without_a_rulebook_stat_block() {
        RuntimeEvidence story = evidence(RuntimeEvidenceType.STORYBOOK, "page-2", "A loud noise echoes.");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> CombatScenarioGroundingPolicy.ground(ScenarioModel.empty(),
                        CurrentSituation.initial("cellar"),
                        List.of(new CombatEnemyProposal("", "giant-rat", "Giant Rat", 1, CombatStartMode.INSTANT)),
                        List.of(story), List.of()));

        assertEquals("COMBAT_STAT_BLOCK_NOT_FOUND", failure.getMessage());
    }

    private static RuntimeEvidence evidence(RuntimeEvidenceType type, String locator, String text) {
        return new RuntimeEvidence(type, new KnowledgeDocumentId(UUID.randomUUID()), 1, locator, text);
    }
}
