package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.Objects;

/** Whitelisted presentation input. It deliberately has no planner context or source evidence. */
public record WriterContext(
        List<String> visibleFacts,
        String visibleScene,
        List<String> styleHints,
        String writingConfiguration) {
    public WriterContext {
        visibleFacts = List.copyOf(Objects.requireNonNull(visibleFacts, "visible facts must not be null"));
        visibleScene = visibleScene == null ? "" : visibleScene.trim();
        styleHints = List.copyOf(Objects.requireNonNull(styleHints, "style hints must not be null"));
        writingConfiguration = writingConfiguration == null ? "" : writingConfiguration.trim();
    }

    public static WriterContext of(ResolvedTurnPlan resolvedPlan) {
        Objects.requireNonNull(resolvedPlan, "resolved plan must not be null");
        return new WriterContext(resolvedPlan.plan().revealableFacts(), resolvedPlan.plan().scene(), List.of(), "");
    }
}
