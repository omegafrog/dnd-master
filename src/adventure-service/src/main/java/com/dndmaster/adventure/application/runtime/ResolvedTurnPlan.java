package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable, engine-resolved artifact shared by persistence and presentation. */
public record ResolvedTurnPlan(TurnPlan plan, List<String> outcomes, RuntimeTurnLifecycle lifecycle,
                               EffectivePromptLineage promptLineage,
                               Map<String, EffectivePromptLineage> promptLineages) {
    public ResolvedTurnPlan {
        plan = Objects.requireNonNull(plan, "turn plan must not be null");
        outcomes = List.copyOf(Objects.requireNonNull(outcomes, "outcomes must not be null"));
        lifecycle = lifecycle == null ? RuntimeTurnLifecycle.RESOLVED_UNCOMMITTED : lifecycle;
        promptLineages = Map.copyOf(promptLineages == null ? Map.of() : promptLineages);
        if (lifecycle != RuntimeTurnLifecycle.RESOLVED_UNCOMMITTED && lifecycle != RuntimeTurnLifecycle.PRESENTED) {
            throw new IllegalArgumentException("resolved artifact must be uncommitted or presented");
        }
    }

    public ResolvedTurnPlan(TurnPlan plan, List<String> outcomes, RuntimeTurnLifecycle lifecycle) {
        this(plan, outcomes, lifecycle, null, Map.of());
    }

    public ResolvedTurnPlan(TurnPlan plan, List<String> outcomes, RuntimeTurnLifecycle lifecycle,
                            EffectivePromptLineage promptLineage) {
        this(plan, outcomes, lifecycle, promptLineage,
                promptLineage == null ? Map.of() : Map.of(promptLineage.role(), promptLineage));
    }

    public static ResolvedTurnPlan of(TurnPlan plan, List<String> outcomes) {
        return new ResolvedTurnPlan(plan, outcomes, RuntimeTurnLifecycle.RESOLVED_UNCOMMITTED, null, Map.of());
    }

    public RuntimeTurnLifecycle lifecycle() { return lifecycle; }

    public ResolvedTurnPlan withPromptLineage(EffectivePromptLineage lineage) {
        EffectivePromptLineage required = Objects.requireNonNull(lineage, "prompt lineage must not be null");
        Map<String, EffectivePromptLineage> next = new java.util.LinkedHashMap<>(promptLineages);
        next.put(required.role(), required);
        return new ResolvedTurnPlan(plan, outcomes, lifecycle, required, next);
    }

    public ResolvedTurnPlan withPromptLineages(Map<String, EffectivePromptLineage> lineages) {
        Map<String, EffectivePromptLineage> next = Map.copyOf(Objects.requireNonNull(lineages, "prompt lineages must not be null"));
        EffectivePromptLineage primary = next.get("PLANNER");
        if (primary == null && !next.isEmpty()) primary = next.values().iterator().next();
        return new ResolvedTurnPlan(plan, outcomes, lifecycle, primary, next);
    }

    public ResolvedTurnPlan presented() {
        return lifecycle == RuntimeTurnLifecycle.PRESENTED ? this
                : new ResolvedTurnPlan(plan, outcomes, RuntimeTurnLifecycle.PRESENTED, promptLineage, promptLineages);
    }
}
