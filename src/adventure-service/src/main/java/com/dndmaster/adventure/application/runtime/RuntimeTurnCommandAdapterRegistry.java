package com.dndmaster.adventure.application.runtime;

import java.util.Map;
import java.util.Objects;

/** Dispatches recovered commands to the owning bounded-context adapter. */
public final class RuntimeTurnCommandAdapterRegistry implements RuntimeTurnCommandAdapter {
    private final Map<String, RuntimeTurnCommandAdapter> adapters;
    private final RuntimeTurnCommandAdapter fallback;

    public RuntimeTurnCommandAdapterRegistry(Map<String, RuntimeTurnCommandAdapter> adapters,
            RuntimeTurnCommandAdapter fallback) {
        this.adapters = Map.copyOf(Objects.requireNonNull(adapters, "command adapters must not be null"));
        this.fallback = Objects.requireNonNull(fallback, "fallback command adapter must not be null");
    }

    @Override public RuntimeTurnCommandExecution execute(RuntimeTurnCommand command) {
        return adapters.getOrDefault(command.commandType(), fallback).execute(command);
    }
}
