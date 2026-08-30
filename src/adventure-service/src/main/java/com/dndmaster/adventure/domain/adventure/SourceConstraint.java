package com.dndmaster.adventure.domain.adventure;

import java.util.List;

public record SourceConstraint(String id, String fieldPath, String normalizedClaim, List<String> citationKeys) {
    public SourceConstraint {
        id = required(id, "source constraint id");
        fieldPath = required(fieldPath, "source constraint field path");
        normalizedClaim = required(normalizedClaim, "source constraint claim");
        citationKeys = citationKeys == null ? List.of() : citationKeys.stream().map(value -> required(value, "citation key")).distinct().toList();
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
