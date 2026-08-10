package com.dndmaster.ruleknowledge.domain.document.normalized;

public record NormalizedWarning(String code, String severity, String message) {
    public NormalizedWarning {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code must not be blank");
        severity = severity == null || severity.isBlank() ? "WARNING" : severity;
        message = message == null ? "" : message;
    }
}
