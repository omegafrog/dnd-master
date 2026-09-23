package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.application.runtime.PlayerCombatIntentPolicy;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.runtime.CurrentSituation;
import com.dndmaster.adventure.domain.scenario.ScenarioModel;
import com.dndmaster.adventure.domain.scenario.ScenarioModelElement;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerCombatIntentPolicyTest {
    @Test
    void explicit_player_combat_choice_selects_the_matching_supported_scenario() {
        var model = model();
        var situation = new CurrentSituation(UUID.randomUUID(), 1, "지하실", "거대 쥐가 길을 막음",
                "거대 쥐", "통로를 확보한다");

        var enemies = PlayerCombatIntentPolicy.proposalsFor("거대 쥐와 싸우고 싶어", situation, model);

        assertEquals(1, enemies.size());
        assertEquals("cellar-rats", enemies.getFirst().scenarioId());
    }

    @Test
    void declines_to_start_when_the_player_explicitly_avoids_combat() {
        assertEquals(List.of(), PlayerCombatIntentPolicy.proposalsFor("거대 쥐를 공격하지 않고 지나간다",
                new CurrentSituation(UUID.randomUUID(), 1, "지하실", "거대 쥐", "거대 쥐", "지나간다"), model()));
        assertEquals(List.of(), PlayerCombatIntentPolicy.proposalsFor("I don't want to fight the giant rats",
                new CurrentSituation(UUID.randomUUID(), 1, "cellar", "giant rats", "giant rats", "leave"), model()));
    }

    @Test
    void korean_modifier_forms_still_match_the_saved_hostile_name() {
        var enemies = PlayerCombatIntentPolicy.proposalsFor("눈앞의 거대한 쥐를 공격한다",
                new CurrentSituation(UUID.randomUUID(), 1, "지하실", "거대한 쥐가 길을 막음", "거대한 쥐", "통로를 확보한다"), model());

        assertEquals("cellar-rats", enemies.getFirst().scenarioId());
    }

    private static ScenarioModel model() {
        UUID storybookId = UUID.randomUUID();
        var encounter = new ScenarioModelElement("cellar-rats", "combat-scenario", Map.of(
                "enemyKey", "giant-rat", "displayName", "거대 쥐", "count", 8, "location", "지하실"),
                List.of(new ScenarioSourceReference(new KnowledgeDocumentId(storybookId), 1, "page:2")));
        return new ScenarioModel(1, List.of(), List.of(), List.of(), List.of(), List.of(encounter), List.of(), List.of(), "");
    }
}
