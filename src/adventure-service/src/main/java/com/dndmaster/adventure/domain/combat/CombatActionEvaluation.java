package com.dndmaster.adventure.domain.combat;

import java.util.List;

public record CombatActionEvaluation(boolean accepted, TurnResourceCost cost, List<String> violations) {
    public CombatActionEvaluation {
        if (cost == null) throw new IllegalArgumentException("cost must not be null");
        violations = List.copyOf(violations);
        if (accepted && !violations.isEmpty()) throw new IllegalArgumentException("accepted action has violations");
        if (!accepted && violations.isEmpty()) throw new IllegalArgumentException("rejected action needs violations");
    }

    public static CombatActionEvaluation accepted(TurnResourceCost cost) {
        return new CombatActionEvaluation(true, cost, List.of());
    }

    public static CombatActionEvaluation rejected(String violation) {
        return new CombatActionEvaluation(false, new TurnResourceCost(0, false, false, false), List.of(violation));
    }
}
