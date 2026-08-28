package com.dndmaster.aigamemaster.infrastructure.ai;

import java.util.Locale;
import java.util.UUID;

/** The provider choice requested by a session or caller. It is never evidence of execution. */
public record RequestedGmProviderSelection(UUID endpointId, String provider, String model, String reasoning) {
    public RequestedGmProviderSelection {
        provider = required(provider, "provider").toLowerCase(Locale.ROOT);
        model = required(model, "model");
        reasoning = required(reasoning, "reasoning").toLowerCase(Locale.ROOT);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " required");
        return value.trim();
    }
}
