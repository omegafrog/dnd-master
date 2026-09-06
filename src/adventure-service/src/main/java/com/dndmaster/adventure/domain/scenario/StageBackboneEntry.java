package com.dndmaster.adventure.domain.scenario;

import java.util.List;
import java.util.Objects;

public record StageBackboneEntry(String stageId, int order, String role, String coreProblem,
                                String funnelSummary, List<ScenarioSourceReference> sourceRefs) {
    public StageBackboneEntry {
        required(stageId, "stage id"); required(role, "stage role");
        required(coreProblem, "core problem"); required(funnelSummary, "funnel summary");
        if (order < 1) throw new IllegalArgumentException("stage order must be positive");
        sourceRefs = List.copyOf(Objects.requireNonNull(sourceRefs, "stage source refs must not be null"));
        if (sourceRefs.isEmpty()) throw new IllegalArgumentException("stage needs grounding");
    }
    private static void required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
