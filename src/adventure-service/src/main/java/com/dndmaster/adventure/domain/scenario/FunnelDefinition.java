package com.dndmaster.adventure.domain.scenario;

import java.util.List;
import java.util.Objects;

public record FunnelDefinition(String meaning, List<String> requiredRevelationIds,
                               List<ScenarioSourceReference> sourceRefs) {
    public FunnelDefinition {
        if (meaning == null || meaning.isBlank()) throw new IllegalArgumentException("funnel meaning is required");
        requiredRevelationIds = List.copyOf(Objects.requireNonNull(requiredRevelationIds, "funnel references must not be null"));
        sourceRefs = List.copyOf(Objects.requireNonNull(sourceRefs, "funnel source refs must not be null"));
        if (sourceRefs.isEmpty()) throw new IllegalArgumentException("funnel needs grounding");
    }
}
