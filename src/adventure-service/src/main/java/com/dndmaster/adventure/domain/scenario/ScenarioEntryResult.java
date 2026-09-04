package com.dndmaster.adventure.domain.scenario;

import java.util.List;
import java.util.Objects;

/** Source-backed preparation result consumed when the first Story Plan is built. */
public record ScenarioEntryResult(
        Decision decision,
        String entryPoint,
        String startPremise,
        List<ScenarioSourceReference> evidence,
        String sourceAnchor) {
    public enum Decision { EXPLICIT_SOURCE, INFERRED_SOURCE, MINIMAL_PROLOGUE }

    public ScenarioEntryResult {
        decision = Objects.requireNonNull(decision, "entry decision must not be null");
        entryPoint = required(entryPoint, "entry point");
        startPremise = required(startPremise, "start premise");
        evidence = List.copyOf(Objects.requireNonNull(evidence, "entry evidence must not be null"));
        sourceAnchor = required(sourceAnchor, "source anchor");
        if (decision != Decision.MINIMAL_PROLOGUE && evidence.isEmpty()) {
            throw new IllegalArgumentException("source entry decision requires evidence");
        }
    }

    public boolean requiresPrologue() { return decision == Decision.MINIMAL_PROLOGUE; }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
