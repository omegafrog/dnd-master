package com.dndmaster.adventure.application.runtime;

import java.util.Objects;
import java.util.UUID;

/** Immutable input for authoritative resolution. Providers never create state from prose. */
public record DeterministicAdjudicationRequest(
        UUID commandId,
        UUID sessionId,
        UUID turnId,
        String action,
        String stateFingerprint,
        long seed,
        long expectedVersion) {
    public DeterministicAdjudicationRequest {
        commandId = Objects.requireNonNull(commandId, "command id must not be null");
        sessionId = Objects.requireNonNull(sessionId, "session id must not be null");
        turnId = Objects.requireNonNull(turnId, "turn id must not be null");
        action = required(action, "action");
        stateFingerprint = required(stateFingerprint, "state fingerprint");
        if (expectedVersion < 0) throw new IllegalArgumentException("expected version must not be negative");
    }

    public String fingerprint() {
        return commandId + "|" + sessionId + "|" + turnId + "|" + action + "|"
                + stateFingerprint + "|" + seed + "|" + expectedVersion;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
