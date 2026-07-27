package com.dndmaster.adventure.application.scenario.compilation;

import java.util.Objects;
import java.util.UUID;

public record WorkEnvelope(
        UUID workId, String workType, UUID aggregateId, long inputVersion, String idempotencyKey, int attempt) {
    public WorkEnvelope {
        workId = Objects.requireNonNull(workId, "work id must not be null");
        workType = Objects.requireNonNull(workType, "work type must not be null");
        aggregateId = Objects.requireNonNull(aggregateId, "aggregate id must not be null");
        idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotency key must not be null");
        if (inputVersion <= 0 || attempt < 0) throw new IllegalArgumentException("invalid work envelope");
    }
}
