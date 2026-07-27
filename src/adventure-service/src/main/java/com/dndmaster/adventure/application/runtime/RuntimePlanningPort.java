package com.dndmaster.adventure.application.runtime;

public interface RuntimePlanningPort {
    RuntimePlan plan(RuntimePlanningRequest request);
}
