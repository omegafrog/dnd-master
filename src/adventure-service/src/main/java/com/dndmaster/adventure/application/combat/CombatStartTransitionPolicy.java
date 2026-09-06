package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.application.runtime.CombatEnemyProposal;
import com.dndmaster.adventure.domain.runtime.CurrentSituation;
import java.util.List;
import java.util.Objects;

/** Ensures encounter materialization follows a committed combat-situation transition. */
public final class CombatStartTransitionPolicy {
    private CombatStartTransitionPolicy() {}

    public static void requireCommittedCombatSituation(CurrentSituation situation,
            List<CombatEnemyProposal> enemies) {
        Objects.requireNonNull(situation, "current situation must not be null");
        if (enemies == null || enemies.isEmpty()) {
            throw new IllegalArgumentException("COMBAT_SCENARIO_REQUIRED");
        }
        String activeScenarioId = situation.activeCombatScenarioId();
        if (activeScenarioId == null || enemies.stream().anyMatch(enemy -> enemy == null
                || !activeScenarioId.equals(enemy.scenarioId()))) {
            throw new IllegalArgumentException("COMBAT_SITUATION_NOT_COMMITTED");
        }
        if (enemies.stream().anyMatch(enemy -> enemy.statBlock() == null)) {
            throw new IllegalArgumentException("COMBAT_STAT_BLOCK_NOT_FOUND");
        }
    }
}
