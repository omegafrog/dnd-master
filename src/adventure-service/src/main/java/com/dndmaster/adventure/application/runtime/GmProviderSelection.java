package com.dndmaster.adventure.application.runtime;

public record GmProviderSelection(String provider, String model, String reasoning) {
    public GmProviderSelection {
        provider = required(provider, "provider");
        model = required(model, "model");
        reasoning = required(reasoning, "reasoning");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " required");
        return value.trim();
    }
}
