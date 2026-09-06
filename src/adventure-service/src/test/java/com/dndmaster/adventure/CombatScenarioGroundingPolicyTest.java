package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.runtime.CombatEnemyProposal;
import com.dndmaster.adventure.application.runtime.CombatScenarioGroundingPolicy;
import com.dndmaster.adventure.application.runtime.CombatStartMode;
import com.dndmaster.adventure.application.runtime.RuntimeEvidence;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceType;
import com.dndmaster.adventure.domain.runtime.CurrentSituation;
import com.dndmaster.adventure.domain.scenario.ScenarioModel;
import com.dndmaster.adventure.domain.scenario.ScenarioModelElement;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatScenarioGroundingPolicyTest {
    @Test
    void rejects_enemy_name_without_a_scenario_combat_reference() {
        ScenarioModel model = modelWithRatCombat();

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> CombatScenarioGroundingPolicy.ground(model, CurrentSituation.initial("cellar"),
                        List.of(new CombatEnemyProposal("고블린"))));

        assertEquals("COMBAT_SCENARIO_REFERENCE_REQUIRED", failure.getMessage());
    }

    @Test
    void grounds_only_a_scenario_combat_reference_and_preserves_its_quantity() {
        ScenarioModel model = modelWithRatCombat();

        var grounded = CombatScenarioGroundingPolicy.ground(model, CurrentSituation.initial("cellar"),
                List.of(new CombatEnemyProposal("cellar-rat-ambush", "Giant Rat", 8)));

        assertEquals("cellar-rat-ambush", grounded.get(0).scenarioId());
        assertEquals("giant-rat", grounded.get(0).enemyKey());
        assertEquals(8, grounded.get(0).count());
    }

    @Test
    void grounds_a_combat_from_the_current_situation_when_story_and_rulebook_evidence_support_it() {
        RuntimeEvidence story = evidence(RuntimeEvidenceType.STORYBOOK, "cellar-rats",
                "Giant Rats nest behind the barrels in the cellar.");
        RuntimeEvidence rules = evidence(RuntimeEvidenceType.RULEBOOK, "monster-manual-335",
                "Giant Rat Armor Class 12 Hit Points 7 (2d6). Bite. Melee Weapon Attack: +4 to hit.");

        var grounded = CombatScenarioGroundingPolicy.ground(ScenarioModel.empty(), CurrentSituation.initial("cellar"),
                List.of(new CombatEnemyProposal("", "giant-rat", "거대 쥐", 2, CombatStartMode.SITUATION)),
                List.of(story), List.of(rules));

        assertEquals(CombatStartMode.SITUATION, grounded.get(0).mode());
        assertEquals(2, grounded.get(0).count());
        assertEquals(12, grounded.get(0).statBlock().armorClass());
    }

    @Test
    void grounds_a_combat_from_a_persisted_current_situation_without_requiring_the_same_story_excerpt_again() {
        RuntimeEvidence rules = evidence(RuntimeEvidenceType.RULEBOOK, "monster-manual-335",
                "Giant Rat Armor Class 12 Hit Points 7 (2d6). Bite. Melee Weapon Attack: +4 to hit.");
        CurrentSituation situation = new CurrentSituation(UUID.randomUUID(), 1, "cellar",
                "Rats are hidden behind the barrels.", "Eight Giant Rats are attacking the party.",
                "Defeat the giant rats");

        var grounded = CombatScenarioGroundingPolicy.ground(ScenarioModel.empty(), situation,
                List.of(new CombatEnemyProposal("", "giant-rat", "거대 쥐", 1, CombatStartMode.SITUATION)),
                List.of(), List.of(rules));

        assertEquals(CombatStartMode.SITUATION, grounded.get(0).mode());
        assertEquals(12, grounded.get(0).statBlock().armorClass());
    }

    @Test
    void reads_korean_rulebook_combat_numbers_for_a_situation_enemy() {
        RuntimeEvidence rules = evidence(RuntimeEvidenceType.RULEBOOK, "basic-rules-123",
                "거대 쥐 Giant Rat\n방어도 12\n히트 포인트 7 (2d6)\n물기. 근접 무기 공격: 명중 +4, 간격 5ft, 목표 하나.");
        CurrentSituation situation = new CurrentSituation(UUID.randomUUID(), 1, "지하실",
                "통 뒤에 쥐가 숨어 있다.", "거대 쥐가 파티를 공격한다.", "쥐를 물리친다.");

        var grounded = CombatScenarioGroundingPolicy.ground(ScenarioModel.empty(), situation,
                List.of(new CombatEnemyProposal("", "giant-rat", "거대 쥐", 1, CombatStartMode.SITUATION)),
                List.of(), List.of(rules));

        assertEquals(12, grounded.get(0).statBlock().armorClass());
        assertEquals(7, grounded.get(0).statBlock().hitPointMaximum());
        assertEquals(4, grounded.get(0).statBlock().attackModifier());
    }

    private static ScenarioModel modelWithRatCombat() {
        ScenarioModelElement combat = new ScenarioModelElement("cellar-rat-ambush", "combat-scenario",
                Map.of("enemyKey", "giant-rat", "displayName", "Giant Rat", "count", 8,
                        "location", "cellar"), List.of(sourceRef()));
        return new ScenarioModel(1, List.of(), List.of(), List.of(), List.of(), List.of(combat), List.of(),
                List.of(new ScenarioModelElement("resolution", "resolution", Map.of("value", "escape"), List.of(sourceRef()))),
                "The party enters the cellar.");
    }

    private static ScenarioSourceReference sourceRef() {
        return new ScenarioSourceReference(new KnowledgeDocumentId(UUID.randomUUID()), 1, "page-2");
    }

    private static RuntimeEvidence evidence(RuntimeEvidenceType type, String locator, String text) {
        return new RuntimeEvidence(type, new KnowledgeDocumentId(UUID.randomUUID()), 1, locator, text);
    }
}
