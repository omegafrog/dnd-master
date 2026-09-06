package com.dndmaster.adventure.domain.scenario;

import java.util.List;
import java.util.Objects;

public record SituationDefinition(String situationId, List<StageIntent> intents,
                                  List<String> revelationIds, List<String> pressureIds, List<String> funnelIds,
                                  List<String> funnelPredicateIds, List<String> importantConsequenceIds, boolean opening,
                                  List<ScenarioSourceReference> sourceRefs) {
    public SituationDefinition {
        if (situationId == null || situationId.isBlank()) throw new IllegalArgumentException("situation id is required");
        intents = List.copyOf(Objects.requireNonNull(intents, "situation intents must not be null"));
        revelationIds = List.copyOf(Objects.requireNonNull(revelationIds, "situation revelation ids must not be null"));
        pressureIds = List.copyOf(Objects.requireNonNull(pressureIds, "situation pressure ids must not be null"));
        funnelIds = List.copyOf(Objects.requireNonNull(funnelIds, "situation funnel ids must not be null"));
        funnelPredicateIds = List.copyOf(Objects.requireNonNull(funnelPredicateIds, "situation funnel predicate ids must not be null"));
        importantConsequenceIds = List.copyOf(Objects.requireNonNull(importantConsequenceIds, "situation consequence ids must not be null"));
        sourceRefs = List.copyOf(Objects.requireNonNull(sourceRefs, "situation source refs must not be null"));
        if (intents.isEmpty()) throw new IllegalArgumentException("situation must have a stage intent");
        if (revelationIds.isEmpty() && pressureIds.isEmpty() && funnelIds.isEmpty() && funnelPredicateIds.isEmpty() && importantConsequenceIds.isEmpty()) {
            throw new IllegalArgumentException("situation must reference a stage intent target");
        }
    }

    public SituationDefinition(String situationId, List<StageIntent> intents, List<ScenarioSourceReference> sourceRefs) {
        this(situationId, intents, List.of(), List.of(), List.of(), List.of(), List.of(), false, sourceRefs);
    }
}
