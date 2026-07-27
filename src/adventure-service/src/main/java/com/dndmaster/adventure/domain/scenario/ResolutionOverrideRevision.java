package com.dndmaster.adventure.domain.scenario;

import java.time.Instant;
import java.util.Objects;

public record ResolutionOverrideRevision(
        long revision,
        String author,
        String reason,
        Instant createdAt,
        Instant updatedAt,
        ResolutionOverrideStatus status) {
    public ResolutionOverrideRevision {
        if (revision <= 0) {
            throw new IllegalArgumentException("revision must be positive");
        }
        author = Objects.requireNonNull(author, "author must not be null");
        reason = Objects.requireNonNull(reason, "reason must not be null");
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        status = Objects.requireNonNull(status, "status must not be null");
    }
}
