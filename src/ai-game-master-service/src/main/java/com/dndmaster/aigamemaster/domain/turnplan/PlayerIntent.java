package com.dndmaster.aigamemaster.domain.turnplan;

import java.util.List;

public record PlayerIntent(String action, String goal, List<String> targets) {
    public PlayerIntent {
        action = TurnPlan.required(action, "action");
        goal = TurnPlan.required(goal, "goal");
        targets = TurnPlan.copy(targets, "targets").stream().map(v -> TurnPlan.required(v, "target")).toList();
        if (targets.isEmpty()) throw new IllegalArgumentException("targets required");
    }
}
