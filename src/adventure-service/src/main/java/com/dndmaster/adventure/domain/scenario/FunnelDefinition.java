package com.dndmaster.adventure.domain.scenario;

import java.util.List;
import java.util.Objects;

public record FunnelDefinition(String funnelId, String meaning, List<String> requiredRevelationIds,
                               List<String> requiredPredicateIds,
                               List<ScenarioSourceReference> sourceRefs) {
    public FunnelDefinition {
        if (funnelId == null || funnelId.isBlank()) throw new IllegalArgumentException("funnel id is required");
        if (meaning == null || meaning.isBlank()) throw new IllegalArgumentException("funnel meaning is required");
        requiredRevelationIds = List.copyOf(Objects.requireNonNull(requiredRevelationIds, "funnel references must not be null"));
        requiredPredicateIds = List.copyOf(Objects.requireNonNull(requiredPredicateIds, "funnel predicate ids must not be null"));
        sourceRefs = List.copyOf(Objects.requireNonNull(sourceRefs, "funnel source refs must not be null"));
    }
    public FunnelDefinition(String meaning, List<String> requiredRevelationIds, List<ScenarioSourceReference> sourceRefs) {
        this("funnel", meaning, requiredRevelationIds, List.of(), sourceRefs);
    }
    public FunnelDefinition(String meaning, List<String> requiredRevelationIds, List<String> requiredPredicateIds,
            List<ScenarioSourceReference> sourceRefs) {
        this("funnel", meaning, requiredRevelationIds, requiredPredicateIds, sourceRefs);
    }
}
