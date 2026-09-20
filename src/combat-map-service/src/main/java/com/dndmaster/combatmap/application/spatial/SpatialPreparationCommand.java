package com.dndmaster.combatmap.application.spatial;

import java.util.Objects;
import java.util.UUID;

/** The durable identity and optimistic-concurrency contract for one preparation attempt. */
public record SpatialPreparationCommand(UUID commandId, String fingerprint, long expectedVersion) {
    public SpatialPreparationCommand {
        commandId = Objects.requireNonNull(commandId, "preparation command id must not be null");
        if (fingerprint == null || fingerprint.isBlank()) {
            throw new IllegalArgumentException("preparation fingerprint must not be blank");
        }
        fingerprint = fingerprint.trim();
        if (expectedVersion < 0) throw new IllegalArgumentException("preparation expected version must not be negative");
    }
}
