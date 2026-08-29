package com.dndmaster.adventure.application.runtime;

import java.util.Objects;

public record VerificationViolation(VerificationViolationType type, VerificationSeverity severity,
                                    String evidence, String location, String instruction) {
    public VerificationViolation {
        type = Objects.requireNonNull(type);
        severity = Objects.requireNonNull(severity);
        evidence = evidence == null ? "" : evidence.trim();
        location = location == null ? "draft" : location.trim();
        instruction = instruction == null ? "" : instruction.trim();
    }
}
