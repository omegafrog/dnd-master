package com.dndmaster.adventure.application.runtime;

public record GmAgentFailure(String category, boolean retryable, String safeMessage, String correlationId) {
    public GmAgentFailure {
        if (category == null || category.isBlank() || safeMessage == null || safeMessage.isBlank()
                || correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("complete GM failure contract required");
        }
        category = category.trim();
        safeMessage = safeMessage.trim();
        correlationId = correlationId.trim();
    }
}
