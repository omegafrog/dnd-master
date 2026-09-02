package com.dndmaster.adventure.domain.adventure;

import java.util.List;

public record SourceFactClaim(String fieldPath, String normalizedClaim, List<String> citationKeys, ClaimOrigin origin) {
    public SourceFactClaim(String fieldPath, String normalizedClaim, List<String> citationKeys) {
        this(fieldPath, normalizedClaim, citationKeys, ClaimOrigin.SOURCE);
    }

    public SourceFactClaim {
        fieldPath = required(fieldPath, "source fact field path");
        normalizedClaim = required(normalizedClaim, "source fact claim");
        citationKeys = citationKeys == null ? List.of() : citationKeys.stream()
                .map(value -> required(value, "source fact citation key")).distinct().toList();
        origin = origin == null ? ClaimOrigin.UNKNOWN : origin;
        if (origin == ClaimOrigin.SOURCE && citationKeys.isEmpty()) throw new IllegalArgumentException("source fact claim requires citation keys");
    }

    public SourceFactClaim(String fieldPath, String normalizedClaim, String citationKey) {
        this(fieldPath, normalizedClaim, List.of(citationKey));
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
