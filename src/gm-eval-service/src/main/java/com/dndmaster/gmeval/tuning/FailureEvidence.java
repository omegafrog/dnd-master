package com.dndmaster.gmeval.tuning;

import java.util.Objects;

public record FailureEvidence(String evidenceId, TuningFailureCategory category,
                              boolean resolved, String sourceRef) {
    public FailureEvidence {
        evidenceId = required(evidenceId, "evidence id");
        category = Objects.requireNonNull(category, "failure category required");
        sourceRef = required(sourceRef, "evidence source reference");
    }

    public static FailureEvidence resolved(String evidenceId, TuningFailureCategory category) {
        return new FailureEvidence(evidenceId, category, true, "evidence/" + evidenceId);
    }

    public static FailureEvidence unresolved(String evidenceId, TuningFailureCategory category) {
        return new FailureEvidence(evidenceId, category, false, "evidence/" + evidenceId);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " required");
        return value.trim();
    }
}
