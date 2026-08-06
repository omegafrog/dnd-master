package com.dndmaster.adventure.application.runtime;

import java.util.Objects;
import java.util.UUID;

public record GmToolInvocation(UUID invocationId, UUID sessionId, UUID turnId, UUID ownerPlayerId,
                               String toolName, String argumentsJson) {
    public GmToolInvocation {
        Objects.requireNonNull(invocationId); Objects.requireNonNull(sessionId); Objects.requireNonNull(turnId);
        Objects.requireNonNull(ownerPlayerId); Objects.requireNonNull(toolName); Objects.requireNonNull(argumentsJson);
        if (toolName.isBlank() || argumentsJson.isBlank()) throw new IllegalArgumentException("tool invocation is incomplete");
    }
}
