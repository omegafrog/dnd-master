package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.Objects;

/** Provider-neutral, read-only result. State mutation belongs to later tool slices. */
public record GmPlanResult(
        RuntimePlan plan,
        String provider,
        String model,
        String reasoning,
        List<String> stateDelta,
        List<GmToolCall> toolCalls) {
    public GmPlanResult {
        plan = Objects.requireNonNull(plan, "plan must not be null");
        provider = required(provider, "provider");
        model = required(model, "model");
        reasoning = reasoning == null ? "" : reasoning.trim();
        stateDelta = List.copyOf(Objects.requireNonNull(stateDelta, "state delta must not be null"));
        toolCalls = List.copyOf(Objects.requireNonNull(toolCalls, "tool calls must not be null"));
    }

    public GmPlanResult(RuntimePlan plan, String provider, String model, String reasoning, List<String> stateDelta) {
        this(plan, provider, model, reasoning, stateDelta, List.of());
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
