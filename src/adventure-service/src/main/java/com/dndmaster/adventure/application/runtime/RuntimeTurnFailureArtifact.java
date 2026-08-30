package com.dndmaster.adventure.application.runtime;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RuntimeTurnFailureArtifact(
        UUID artifactId,
        UUID turnId,
        RuntimeTurnFailureCode failureCode,
        RuntimeTurnFailureStage stage,
        boolean retryable,
        String rootCauseClass,
        UUID correlationId,
        int attempt,
        Instant occurredAt) {
    public RuntimeTurnFailureArtifact {
        artifactId = Objects.requireNonNull(artifactId, "artifact id must not be null");
        turnId = Objects.requireNonNull(turnId, "turn id must not be null");
        failureCode = Objects.requireNonNull(failureCode, "failure code must not be null");
        stage = Objects.requireNonNull(stage, "failure stage must not be null");
        if (rootCauseClass == null || rootCauseClass.isBlank()) throw new IllegalArgumentException("root cause class must not be blank");
        correlationId = Objects.requireNonNull(correlationId, "correlation id must not be null");
        if (attempt < 1) throw new IllegalArgumentException("attempt must be positive");
        occurredAt = Objects.requireNonNull(occurredAt, "occurred at must not be null");
    }
}
