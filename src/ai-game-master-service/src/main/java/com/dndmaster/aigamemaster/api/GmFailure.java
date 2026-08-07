package com.dndmaster.aigamemaster.api;

import java.util.Objects;

public record GmFailure(GmFailureCategory category, boolean retryable, String safeMessage, String correlationId) {
    public GmFailure {
        Objects.requireNonNull(category);
        if (safeMessage == null || safeMessage.isBlank()) throw new IllegalArgumentException("safe message required");
        if (correlationId == null || correlationId.isBlank()) throw new IllegalArgumentException("correlation id required");
        safeMessage = safeMessage.trim();
        correlationId = correlationId.trim();
    }
}
