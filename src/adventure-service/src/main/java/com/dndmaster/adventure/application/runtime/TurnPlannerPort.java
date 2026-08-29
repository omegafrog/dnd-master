package com.dndmaster.adventure.application.runtime;

/** Decision-only planner boundary. */
public interface TurnPlannerPort {
    TurnPlan plan(PlannerContext context);
}
