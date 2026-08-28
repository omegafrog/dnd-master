package com.dndmaster.aigamemaster.api;

import java.util.Objects;

/** Safe, structured feedback used to repair one rejected GM candidate. */
public record GmCandidateViolation(String code, String fieldPath, boolean repairable, String safeMessage) {
    public GmCandidateViolation {
        code = required(code, "code");
        fieldPath = required(fieldPath, "field path");
        safeMessage = required(safeMessage, "safe message");
    }

    public static GmCandidateViolation malformed(String message) {
        String safe = message == null || message.isBlank() ? "candidate does not satisfy the structured contract" : message;
        return new GmCandidateViolation("GM_CANDIDATE_MALFORMED", "$", true, safe.replaceAll("[\\r\\n]", " "));
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
