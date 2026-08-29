package com.dndmaster.aigamemaster.domain.turnplan;

public final class TurnPlanValidator {
    public void validate(TurnPlan plan) {
        if (plan == null) throw new TurnPlanValidationException("turnPlan required");
        var policy = plan.informationPolicy();
        if (!java.util.Collections.disjoint(policy.requiredFacts(), policy.forbiddenFacts()))
            throw new TurnPlanValidationException("requiredFacts overlap forbiddenFacts");
        if (!java.util.Collections.disjoint(policy.revealableFacts(), policy.forbiddenFacts()))
            throw new TurnPlanValidationException("revealableFacts overlap forbiddenFacts");
    }
}
