package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.dndmaster.adventure.domain.runtime.CurrentSituation;
import org.junit.jupiter.api.Test;

class CurrentSituationCombatTransitionTest {
    @Test
    void combat_scenario_is_recorded_only_when_the_runtime_enters_that_situation() {
        CurrentSituation initial = CurrentSituation.initial("The party enters the cellar.");

        CurrentSituation combat = initial.enterCombatScenario("cellar-rat-ambush");

        assertNull(initial.activeCombatScenarioId());
        assertEquals("cellar-rat-ambush", combat.activeCombatScenarioId());
        assertEquals(initial.revision() + 1, combat.revision());
    }
}
