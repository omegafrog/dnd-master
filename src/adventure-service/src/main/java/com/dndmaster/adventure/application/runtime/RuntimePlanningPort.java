package com.dndmaster.adventure.application.runtime;

public interface RuntimePlanningPort {
    RuntimePlan plan(RuntimePlanningRequest request);

    default RuntimePlanningResult planWithOutcomes(RuntimePlanningRequest request) {
        return new RuntimePlanningResult(plan(request), java.util.List.of());
    }
}
