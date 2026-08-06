package com.dndmaster.adventure.application.runtime;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public record GmToolDefinition(String name, String inputSchema, GmToolHandler handler, Function<UUID, Optional<GmToolOutcome>> query) {
    public GmToolDefinition {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("tool name must not be blank");
        if (inputSchema == null || inputSchema.isBlank()) throw new IllegalArgumentException("tool schema must not be blank");
        Objects.requireNonNull(handler, "tool handler must not be null");
        Objects.requireNonNull(query, "tool query must not be null");
    }
    public static GmToolDefinition of(String name, String inputSchema, GmToolHandler handler) {
        return new GmToolDefinition(name, inputSchema, handler, ignored -> Optional.empty());
    }
    public static GmToolDefinition fromOfficial(String name, String inputSchema, OfficialToolPort port) {
        return new GmToolDefinition(name, inputSchema, port::execute, port::query);
    }
}
