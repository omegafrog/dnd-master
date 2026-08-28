package com.dndmaster.aigamemaster.infrastructure.ai;

import java.time.Instant;
import java.util.Objects;

/** Immutable snapshot of the endpoint configuration actually used for an invocation. */
public record EffectiveGmProviderSelection(
        java.util.UUID endpointId,
        Instant endpointVersion,
        String provider,
        String model,
        String reasoning) {
    public EffectiveGmProviderSelection {
        endpointId = Objects.requireNonNull(endpointId, "endpoint id required");
        endpointVersion = Objects.requireNonNull(endpointVersion, "endpoint version required");
        provider = required(provider, "provider");
        model = required(model, "model");
        reasoning = required(reasoning, "reasoning");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " required");
        return value.trim();
    }
}
