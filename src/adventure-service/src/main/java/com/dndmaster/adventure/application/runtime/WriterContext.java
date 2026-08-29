package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.Objects;

/** Whitelisted presentation input. It deliberately has no planner context or source evidence. */
public record WriterContext(
        List<String> visibleFacts,
        String visibleScene,
        List<ExemplarResult> styleExemplars,
        String writingConfiguration) {
    public WriterContext {
        visibleFacts = List.copyOf(Objects.requireNonNull(visibleFacts, "visible facts must not be null"));
        visibleScene = visibleScene == null ? "" : visibleScene.trim();
        styleExemplars = List.copyOf(Objects.requireNonNull(styleExemplars, "style exemplars must not be null"));
        writingConfiguration = writingConfiguration == null ? "" : writingConfiguration.trim();
    }

    public WriterContext(List<String> visibleFacts, String visibleScene, String writingConfiguration) {
        this(visibleFacts, visibleScene, List.of(), writingConfiguration);
    }

    /** Legacy presentation projection; provenance-bearing exemplars remain available via styleExemplars(). */
    public List<String> styleHints() {
        return styleExemplars.stream().map(exemplar -> exemplar.exemplar().text()).toList();
    }

    public static WriterContext of(ResolvedTurnPlan resolvedPlan) {
        Objects.requireNonNull(resolvedPlan, "resolved plan must not be null");
        return new WriterContext(resolvedPlan.plan().revealableFacts(), resolvedPlan.plan().scene(), List.of(), "");
    }
}
