package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.Objects;

/** Whitelisted presentation input. It deliberately has no planner context or source evidence. */
public record WriterContext(
        ResolvedTurnPlan resolvedPlan,
        String visibleScene,
        List<String> styleHints,
        String writingConfiguration) {
    public WriterContext {
        resolvedPlan = visibleOnly(Objects.requireNonNull(resolvedPlan, "resolved plan must not be null"));
        visibleScene = visibleScene == null ? "" : visibleScene.trim();
        styleHints = List.copyOf(Objects.requireNonNull(styleHints, "style hints must not be null"));
        writingConfiguration = writingConfiguration == null ? "" : writingConfiguration.trim();
    }

    public static WriterContext of(ResolvedTurnPlan resolvedPlan) {
        return new WriterContext(resolvedPlan, resolvedPlan.plan().scene(), List.of(), "");
    }

    private static ResolvedTurnPlan visibleOnly(ResolvedTurnPlan resolved) {
        TurnPlan plan = resolved.plan();
        return new ResolvedTurnPlan(new TurnPlan(plan.scene(), plan.npcState(), plan.judgment(), plan.revealableFacts(), List.of()),
                resolved.outcomes(), resolved.lifecycle());
    }
}
