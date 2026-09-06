package com.dndmaster.adventure.domain.scenario;

import java.util.List;
import java.util.Objects;

public record RevelationDefinition(String revelationId, boolean required, List<ScenarioSourceReference> sourceRefs) {
    public RevelationDefinition {
        if (revelationId == null || revelationId.isBlank()) throw new IllegalArgumentException("revelation id is required");
        sourceRefs = List.copyOf(Objects.requireNonNull(sourceRefs, "revelation source refs must not be null"));
        if (required && sourceRefs.isEmpty()) throw new IllegalArgumentException("required revelation needs grounding");
    }
}
