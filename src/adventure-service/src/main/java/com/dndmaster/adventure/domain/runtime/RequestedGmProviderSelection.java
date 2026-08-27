package com.dndmaster.adventure.domain.runtime;

import java.util.Locale;
import java.util.UUID;

/** The session/requested provider intent retained separately from execution metadata. */
public record RequestedGmProviderSelection(UUID endpointId, String provider, String model, String reasoning) {
    public RequestedGmProviderSelection {
        provider = normalizedProvider(provider);
        model = required(model, "model");
        reasoning = required(reasoning, "reasoning").toLowerCase(Locale.ROOT);
    }

    public static RequestedGmProviderSelection legacyUnknown() {
        return new RequestedGmProviderSelection(null, "LEGACY_UNKNOWN", "LEGACY_UNKNOWN", "LEGACY_UNKNOWN");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " required");
        return value.trim();
    }

    private static String normalizedProvider(String value) {
        String required = required(value, "provider");
        return "LEGACY_UNKNOWN".equalsIgnoreCase(required) ? "LEGACY_UNKNOWN" : required.toLowerCase(Locale.ROOT);
    }
}
