package com.dndmaster.adventure.application.runtime;

import java.util.Objects;
import java.util.UUID;

public record GmToolInvocation(UUID invocationId, UUID sessionId, UUID turnId, UUID ownerPlayerId,
                               String toolName, String argumentsJson, GmToolExecutionContext executionContext,
                               UUID candidateId, Integer toolIndex) {
    public GmToolInvocation(UUID invocationId, UUID sessionId, UUID turnId, UUID ownerPlayerId,
                            String toolName, String argumentsJson) {
        this(invocationId, sessionId, turnId, ownerPlayerId, toolName, argumentsJson, null, null, null);
    }
    public GmToolInvocation(UUID invocationId, UUID sessionId, UUID turnId, UUID ownerPlayerId,
                            String toolName, String argumentsJson, GmToolExecutionContext executionContext) {
        this(invocationId, sessionId, turnId, ownerPlayerId, toolName, argumentsJson, executionContext, null, null);
    }
    public GmToolInvocation {
        Objects.requireNonNull(invocationId); Objects.requireNonNull(sessionId); Objects.requireNonNull(turnId);
        Objects.requireNonNull(ownerPlayerId); Objects.requireNonNull(toolName); Objects.requireNonNull(argumentsJson);
        if (toolName.isBlank() || argumentsJson.isBlank()) throw new IllegalArgumentException("tool invocation is incomplete");
    }
}
