package com.dndmaster.adventure.domain.scenario;

import java.util.List;
import java.util.Objects;

public record PressureDefinition(String progressionMaterial, List<ScenarioSourceReference> sourceRefs) {
    public PressureDefinition {
        if (progressionMaterial == null || progressionMaterial.isBlank()) throw new IllegalArgumentException("pressure material is required");
        sourceRefs = List.copyOf(Objects.requireNonNull(sourceRefs, "pressure source refs must not be null"));
    }
}
