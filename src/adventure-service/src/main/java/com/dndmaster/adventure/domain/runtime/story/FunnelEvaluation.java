package com.dndmaster.adventure.domain.runtime.story;

import com.dndmaster.adventure.domain.scenario.DetailedStage;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Deterministic evaluation of a stage funnel from already accepted runtime facts. */
public record FunnelEvaluation(boolean satisfied, Set<String> missingRevelationIds,
                               Set<String> missingPredicateIds) {
    public FunnelEvaluation {
        missingRevelationIds = Set.copyOf(missingRevelationIds == null ? Set.of() : missingRevelationIds);
        missingPredicateIds = Set.copyOf(missingPredicateIds == null ? Set.of() : missingPredicateIds);
        if (satisfied && (!missingRevelationIds.isEmpty() || !missingPredicateIds.isEmpty())) {
            throw new IllegalArgumentException("satisfied funnel cannot have missing requirements");
        }
    }

    public static FunnelEvaluation evaluate(DetailedStage stage, StoryRuntimeState state) {
        Objects.requireNonNull(stage, "detailed stage must not be null");
        Objects.requireNonNull(state, "story runtime state must not be null");
        Set<String> missingRevelations = new LinkedHashSet<>(stage.funnel().requiredRevelationIds());
        missingRevelations.removeAll(state.learnedRevelationIds());
        Set<String> missingPredicates = new LinkedHashSet<>(stage.funnel().requiredPredicateIds());
        missingPredicates.removeAll(state.satisfiedPredicateIds());
        return new FunnelEvaluation(missingRevelations.isEmpty() && missingPredicates.isEmpty(),
                missingRevelations, missingPredicates);
    }
}
