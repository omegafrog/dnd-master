package com.dndmaster.adventure.domain.scenario;

import java.util.List;
import java.util.Objects;

public record ThreatDefinition(String core, List<ScenarioSourceReference> sourceRefs) {
    public ThreatDefinition {
        if (core == null || core.isBlank()) throw new IllegalArgumentException("threat core is required");
        sourceRefs = List.copyOf(Objects.requireNonNull(sourceRefs, "threat source refs must not be null"));
        if (sourceRefs.isEmpty()) throw new IllegalArgumentException("threat core needs grounding");
    }
}
