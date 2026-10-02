package com.dndmaster.adventure.application.runtime;

public interface RuntimePlanningPort {
    RuntimePlan plan(RuntimePlanningRequest request);

    /** Produces prose only. Implementations must not materialize proposed runtime commands. */
    default RuntimePlan planNarration(RuntimePlanningRequest request) {
        throw new UnsupportedOperationException("narration-only planning is not configured");
    }

    default RuntimePlanningResult planWithOutcomes(RuntimePlanningRequest request) {
        return new RuntimePlanningResult(plan(request), java.util.List.of());
    }
}
