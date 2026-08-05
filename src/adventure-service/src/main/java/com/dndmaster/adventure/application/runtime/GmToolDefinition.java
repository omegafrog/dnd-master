package com.dndmaster.adventure.application.runtime;

import java.util.Objects;

public record GmToolDefinition(String name, String inputSchema, GmToolHandler handler) {
    public GmToolDefinition {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("tool name must not be blank");
        if (inputSchema == null || inputSchema.isBlank()) throw new IllegalArgumentException("tool schema must not be blank");
        Objects.requireNonNull(handler, "tool handler must not be null");
    }
    public static GmToolDefinition of(String name, String inputSchema, GmToolHandler handler) {
        return new GmToolDefinition(name, inputSchema, handler);
    }
}
