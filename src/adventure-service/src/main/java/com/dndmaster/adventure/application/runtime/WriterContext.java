package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.Objects;
import com.dndmaster.adventure.domain.runtime.narrative.NarrativeContext;

/** Whitelisted presentation input. It deliberately has no planner context or source evidence. */
public record WriterContext(
        List<String> visibleFacts,
        String visibleScene,
        List<ExemplarResult> styleExemplars,
        String writingConfiguration,
        NarrativeContext narrativeContext) {
    public WriterContext {
        visibleFacts = List.copyOf(Objects.requireNonNull(visibleFacts, "visible facts must not be null"));
        visibleScene = visibleScene == null ? "" : visibleScene.trim();
        styleExemplars = List.copyOf(Objects.requireNonNull(styleExemplars, "style exemplars must not be null"));
        writingConfiguration = writingConfiguration == null ? "" : writingConfiguration.trim();
        if (narrativeContext != null && visibleFacts.stream().anyMatch(visible -> narrativeContext.worldFacts().stream()
                .noneMatch(fact -> fact.id().equals(visible) || fact.value().equals(visible)))) {
            throw new IllegalArgumentException("writer facts must be projected from the actor narrative context");
        }
    }

    public WriterContext(List<String> visibleFacts, String visibleScene, String writingConfiguration) {
        this(visibleFacts, visibleScene, List.of(), writingConfiguration, null);
    }

    public WriterContext(List<String> visibleFacts, String visibleScene, List<ExemplarResult> styleExemplars,
                         String writingConfiguration) {
        this(visibleFacts, visibleScene, styleExemplars, writingConfiguration, null);
    }

    /** Legacy presentation projection; provenance-bearing exemplars remain available via styleExemplars(). */
    public List<String> styleHints() {
        return styleExemplars.stream().map(exemplar -> exemplar.exemplar().text()).toList();
    }

    public static WriterContext of(ResolvedTurnPlan resolvedPlan) {
        Objects.requireNonNull(resolvedPlan, "resolved plan must not be null");
        return new WriterContext(resolvedPlan.plan().revealableFacts(), resolvedPlan.plan().scene(), List.of(), "", null);
    }

    public static WriterContext of(NarrativeContext actorContext, ResolvedTurnPlan resolvedPlan,
                                  List<ExemplarResult> exemplars) {
        Objects.requireNonNull(actorContext, "actor narrative context must not be null");
        Objects.requireNonNull(resolvedPlan, "resolved plan must not be null");
        List<String> projectedFacts = resolvedPlan.plan().revealableFacts().stream()
                .filter(fact -> actorContext.worldFacts().stream()
                        .anyMatch(world -> world.id().equals(fact) || world.value().equals(fact)))
                .toList();
        return new WriterContext(projectedFacts, resolvedPlan.plan().scene(), exemplars, "", actorContext);
    }
}
