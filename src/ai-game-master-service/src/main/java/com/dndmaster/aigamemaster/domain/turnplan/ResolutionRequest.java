package com.dndmaster.aigamemaster.domain.turnplan;

public record ResolutionRequest(ResolutionType type, String abilityOrSkill, String target, String reason) {
    public ResolutionRequest {
        if (type == null) throw new IllegalArgumentException("resolution type required");
        if (abilityOrSkill != null) abilityOrSkill = TurnPlan.required(abilityOrSkill, "abilityOrSkill");
        target = TurnPlan.required(target, "target");
        reason = TurnPlan.required(reason, "reason");
    }
}
