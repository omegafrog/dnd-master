package com.dndmaster.aigamemaster.infrastructure.ai;

public record GmCandidateViolation(String code, String fieldPath, boolean repairable, String safeMessage) {
    public GmCandidateViolation(String code, String fieldPath, String message) {
        this(code, fieldPath, true, message == null ? "" : message);
    }
}
