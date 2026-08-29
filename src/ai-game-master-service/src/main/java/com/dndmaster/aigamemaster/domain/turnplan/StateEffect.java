package com.dndmaster.aigamemaster.domain.turnplan;

public record StateEffect(String type, String target, String from, String to) {
    public StateEffect {
        type = TurnPlan.required(type, "state effect type");
        target = TurnPlan.required(target, "state effect target");
        if (from != null) from = TurnPlan.required(from, "from");
        if (to != null) to = TurnPlan.required(to, "to");
    }
}
