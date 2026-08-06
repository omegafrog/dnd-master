package com.dndmaster.aigamemaster.infrastructure.ai;

import java.util.Locale;

public record GmProviderRequest(String provider, String model, String reasoning) {
    public GmProviderRequest {
        provider = required(provider, "provider").toLowerCase(Locale.ROOT);
        model = required(model, "model");
        reasoning = required(reasoning, "reasoning").toLowerCase(Locale.ROOT);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " required");
        return value.trim();
    }
}
