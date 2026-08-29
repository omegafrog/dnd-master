package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.Objects;

/** Immutable, engine-resolved artifact shared by persistence and presentation. */
public record ResolvedTurnPlan(TurnPlan plan, List<String> outcomes, RuntimeTurnLifecycle lifecycle,
                               EffectivePromptLineage promptLineage) {
    public ResolvedTurnPlan {
        plan = Objects.requireNonNull(plan, "turn plan must not be null");
        outcomes = List.copyOf(Objects.requireNonNull(outcomes, "outcomes must not be null"));
        lifecycle = lifecycle == null ? RuntimeTurnLifecycle.RESOLVED_UNCOMMITTED : lifecycle;
        if (lifecycle != RuntimeTurnLifecycle.RESOLVED_UNCOMMITTED && lifecycle != RuntimeTurnLifecycle.PRESENTED) {
            throw new IllegalArgumentException("resolved artifact must be uncommitted or presented");
        }
    }

    public ResolvedTurnPlan(TurnPlan plan, List<String> outcomes, RuntimeTurnLifecycle lifecycle) {
        this(plan, outcomes, lifecycle, null);
    }

    public static ResolvedTurnPlan of(TurnPlan plan, List<String> outcomes) {
        return new ResolvedTurnPlan(plan, outcomes, RuntimeTurnLifecycle.RESOLVED_UNCOMMITTED, null);
    }

    public RuntimeTurnLifecycle lifecycle() { return lifecycle; }

    public ResolvedTurnPlan withPromptLineage(EffectivePromptLineage lineage) {
        return new ResolvedTurnPlan(plan, outcomes, lifecycle, Objects.requireNonNull(lineage, "prompt lineage must not be null"));
    }

    public ResolvedTurnPlan presented() {
        return lifecycle == RuntimeTurnLifecycle.PRESENTED ? this
                : new ResolvedTurnPlan(plan, outcomes, RuntimeTurnLifecycle.PRESENTED, promptLineage);
    }
}
