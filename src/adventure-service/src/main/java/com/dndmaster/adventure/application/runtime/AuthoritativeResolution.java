package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.Objects;

/** State-changing result owned by deterministic rules, including audit provenance. */
public record AuthoritativeResolution(
        Status status,
        String outcome,
        List<String> stateChanges,
        List<String> provenance) {
    public enum Status { RESOLVED, REJECTED, PENDING }

    public AuthoritativeResolution {
        status = Objects.requireNonNull(status, "status must not be null");
        outcome = required(outcome, "outcome");
        stateChanges = List.copyOf(Objects.requireNonNull(stateChanges, "state changes must not be null"));
        provenance = List.copyOf(Objects.requireNonNull(provenance, "provenance must not be null"));
        if (provenance.isEmpty()) throw new IllegalArgumentException("provenance must not be empty");
        if (status == Status.RESOLVED && stateChanges.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("resolved state changes must be non-blank");
        }
    }

    public static AuthoritativeResolution resolved(String outcome, List<String> stateChanges, List<String> provenance) {
        return new AuthoritativeResolution(Status.RESOLVED, outcome, stateChanges, provenance);
    }

    public static AuthoritativeResolution pending(String outcome, List<String> provenance) {
        return new AuthoritativeResolution(Status.PENDING, outcome, List.of(), provenance);
    }

    public static AuthoritativeResolution rejected(String outcome, List<String> provenance) {
        return new AuthoritativeResolution(Status.REJECTED, outcome, List.of(), provenance);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
