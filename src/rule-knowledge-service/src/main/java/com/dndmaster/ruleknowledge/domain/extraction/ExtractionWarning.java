package com.dndmaster.ruleknowledge.domain.extraction;

import java.util.Objects;

public record ExtractionWarning(String code, Severity severity, String message) {
    public ExtractionWarning {
        code = require(code, "code");
        severity = Objects.requireNonNull(severity, "severity must not be null");
        message = require(message, "message");
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }

    public enum Severity { INFO, WARNING, ERROR }
}
