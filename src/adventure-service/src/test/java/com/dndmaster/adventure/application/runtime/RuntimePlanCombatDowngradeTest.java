package com.dndmaster.adventure.application.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RuntimePlanCombatDowngradeTest {
    @Test
    void withholding_unverifiable_combat_also_withholds_attack_roll_and_narration() {
        RuntimePlan candidate = new RuntimePlan("beer-cellar", null, "명중 굴림", "장검이 거대 쥐에게 명중하여 피해를 줄 수 있습니다.",
                null, List.of(), List.of(), "provider", "model", "medium", false, "", null, null, 1,
                List.of(), null,
                List.of(new CombatEnemyProposal("encounter", "giant-rats", "Giant Rats", 8, CombatStartMode.SCENARIO)),
                true, false,
                new RuntimeCheckProposal(true, "명중 굴림", "근력", UUID.randomUUID(), RuntimeCheckProposal.RollMethod.PLAYER,
                        "1d20", 2, 12, List.of("RULEBOOK:rat"), "명중", "빗나감"));

        RuntimePlan withheld = candidate.withoutCombat("전투 보류: 룰북 자료 부족");

        assertFalse(withheld.combatStartRequested());
        assertTrue(withheld.combatEnemies().isEmpty());
        assertFalse(withheld.checkProposal().required());
        assertTrue(withheld.narration().contains("전투는 시작되지 않았고 행동은 처리되지 않았습니다"));
        assertFalse(withheld.narration().contains("명중하여 피해"));
    }
}
