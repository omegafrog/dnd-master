package com.dndmaster.adventure.domain.runtime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;

/** Immutable provider identity returned by the invocation router. */
public record EffectiveGmProviderSelection(
        java.util.UUID endpointId,
        Instant endpointVersion,
        String provider,
        String model,
        String reasoning) {
    public EffectiveGmProviderSelection {
        provider = required(provider, "provider");
        model = required(model, "model");
        reasoning = required(reasoning, "reasoning");
        if ("LEGACY_UNKNOWN".equals(provider)) {
            if (endpointId != null || endpointVersion != null) throw new IllegalArgumentException("legacy selection cannot have endpoint identity");
        } else if (endpointId == null || endpointVersion == null) {
            throw new IllegalArgumentException("effective endpoint identity required");
        }
    }

    public static EffectiveGmProviderSelection legacyUnknown() {
        return new EffectiveGmProviderSelection(null, null, "LEGACY_UNKNOWN", "LEGACY_UNKNOWN", "LEGACY_UNKNOWN");
    }

    @JsonIgnore
    public boolean isLegacy() { return "LEGACY_UNKNOWN".equals(provider); }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " required");
        return value.trim();
    }
}
