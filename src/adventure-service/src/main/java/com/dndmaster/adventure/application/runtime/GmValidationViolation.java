package com.dndmaster.adventure.application.runtime;

import java.util.Objects;

public record GmValidationViolation(String code, String fieldPath, boolean repairable, String safeMessage) {
    public GmValidationViolation {
        code = required(code, "violation code");
        fieldPath = required(fieldPath, "field path");
        safeMessage = required(safeMessage, "safe message");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
