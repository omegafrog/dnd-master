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
                situation("cellar", "Giant Rat"),
                List.of(new CombatEnemyProposal("", "giant-rat", "Giant Rat", 1, CombatStartMode.INSTANT)),
                List.of(story), List.of(rules));

        assertEquals(CombatStartMode.INSTANT, grounded.get(0).mode());
        assertEquals("giant-rat", grounded.get(0).enemyKey());
        assertEquals(12, grounded.get(0).statBlock().armorClass());
        org.junit.jupiter.api.Assertions.assertTrue(grounded.get(0).scenarioId().startsWith("instant-"));
    }

    @Test
    void rejects_instant_combat_without_structured_enemy_combat_numbers() {
        RuntimeEvidence story = evidence(RuntimeEvidenceType.STORYBOOK, "page-2", "A loud noise echoes.");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> CombatScenarioGroundingPolicy.ground(ScenarioModel.empty(),
                        situation("cellar", "Giant Rat"),
                        List.of(new CombatEnemyProposal("", "giant-rat", "Giant Rat", 1, CombatStartMode.INSTANT)),
                        List.of(story), List.of()));

        assertEquals("COMBAT_STAT_BLOCK_NOT_FOUND", failure.getMessage());
    }

    @Test
    void reads_combat_numbers_from_additional_rules_but_requires_the_enemy_in_current_situation() {
        RuntimeEvidence story = evidence(RuntimeEvidenceType.STORYBOOK, "page-4",
                "Giant Inferno Spider Armor Class 14 Hit Points 32 (5d10 + 5). "
                        + "Flaming Bite: Melee Weapon Attack: +5 to hit. Hit: 6 (1d8 + 2) piercing damage.");

        var grounded = CombatScenarioGroundingPolicy.ground(ScenarioModel.empty(),
                situation("laboratory", "Giant Inferno Spider"),
                List.of(new CombatEnemyProposal("", "giant-inferno-spider", "Giant Inferno Spider", 1,
                        CombatStartMode.INSTANT)),
                List.of(story), List.of());

        assertEquals(14, grounded.getFirst().statBlock().armorClass());
        assertEquals(32, grounded.getFirst().statBlock().hitPointMaximum());
        assertEquals(5, grounded.getFirst().statBlock().attackModifier());
        assertEquals(story.knowledgeDocumentId().value(),
                grounded.getFirst().statBlock().source().knowledgeDocumentId());
    }

    @Test
    void rules_evidence_alone_does_not_establish_that_an_enemy_is_present() {
        RuntimeEvidence rules = evidence(RuntimeEvidenceType.RULEBOOK, "page-135",
                "Giant Rat Armor Class 12 Hit Points 7 (2d6) Bite. Melee Weapon Attack: +4 to hit.");

        var failure = assertThrows(IllegalArgumentException.class,
                () -> CombatScenarioGroundingPolicy.ground(ScenarioModel.empty(),
                        CurrentSituation.initial("quiet cellar"),
                        List.of(new CombatEnemyProposal("", "giant-rat", "Giant Rat", 1, CombatStartMode.INSTANT)),
                        List.of(), List.of(rules)));

        assertEquals("COMBAT_SCENARIO_NOT_IN_CURRENT_SITUATION", failure.getMessage());
    }

    private static CurrentSituation situation(String location, String threat) {
        return new CurrentSituation(UUID.randomUUID(), 1, location, threat, threat, threat);
    }

    private static RuntimeEvidence evidence(RuntimeEvidenceType type, String locator, String text) {
        return new RuntimeEvidence(type, new KnowledgeDocumentId(UUID.randomUUID()), 1, locator, text);
    }
}
