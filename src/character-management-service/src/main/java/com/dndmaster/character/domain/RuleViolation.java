package com.dndmaster.character.domain;

import java.util.Map;
import java.util.Objects;

public record RuleViolation(
        String code,
        String category,
        String severity,
        String message,
        Map<String, String> parameters) {

    public RuleViolation {
        code = requireText(code, "rule violation code");
        category = requireText(category, "rule violation category");
        severity = requireText(severity, "rule violation severity");
        message = requireText(message, "rule violation message");
        parameters = Map.copyOf(Objects.requireNonNull(parameters, "rule violation parameters must not be null"));
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
        return value;
    }
}
