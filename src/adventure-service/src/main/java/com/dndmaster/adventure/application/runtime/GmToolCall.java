package com.dndmaster.adventure.application.runtime;

import java.util.Objects;

public record GmToolCall(String toolName, String argumentsJson, boolean required) {
    public GmToolCall {
        if (toolName == null || toolName.isBlank()) throw new IllegalArgumentException("tool name required");
        Objects.requireNonNull(argumentsJson, "arguments json required");
    }
}
