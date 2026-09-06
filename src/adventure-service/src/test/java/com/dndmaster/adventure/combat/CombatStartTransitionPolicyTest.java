package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.combat.CombatStartTransitionPolicy;
import com.dndmaster.adventure.application.runtime.CombatEnemyProposal;
import com.dndmaster.adventure.domain.combat.CombatEnemyStatBlock;
import com.dndmaster.adventure.domain.combat.CombatStatBlockSource;
import com.dndmaster.adventure.domain.runtime.CurrentSituation;
import java.util.List;
import org.junit.jupiter.api.Test;

class CombatStartTransitionPolicyTest {
    @Test
    void rejects_materialization_before_the_situation_transition_is_committed() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> CombatStartTransitionPolicy.requireCommittedCombatSituation(
                        CurrentSituation.initial("The party enters the cellar."),
                        List.of(new CombatEnemyProposal("cellar-rat-ambush", "Giant Rat", 8))));

        assertEquals("COMBAT_SITUATION_NOT_COMMITTED", failure.getMessage());
    }

    @Test
    void accepts_only_the_scenario_that_is_active_in_the_committed_situation() {
        CurrentSituation situation = CurrentSituation.initial("The party enters the cellar.")
                .enterCombatScenario("cellar-rat-ambush");

        CombatStartTransitionPolicy.requireCommittedCombatSituation(situation,
                List.of(new CombatEnemyProposal("cellar-rat-ambush", "Giant Rat", 8,
                        com.dndmaster.adventure.application.runtime.CombatStartMode.SCENARIO,
                        new CombatEnemyStatBlock(12, 7, 4, "1d6 + 2",
                                new CombatStatBlockSource(java.util.UUID.randomUUID(), 1, "page-135")))));
    }
}
