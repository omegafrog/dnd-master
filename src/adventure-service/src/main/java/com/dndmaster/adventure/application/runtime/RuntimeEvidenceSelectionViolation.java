package com.dndmaster.adventure.application.runtime;

public record RuntimeEvidenceSelectionViolation(String code, String safeMessage) {
    public RuntimeEvidenceSelectionViolation {
        if (code == null || code.isBlank() || safeMessage == null || safeMessage.isBlank()) {
            throw new IllegalArgumentException("selection violation fields must not be blank");
        }
        code = code.trim();
        safeMessage = safeMessage.trim();
    }
}
