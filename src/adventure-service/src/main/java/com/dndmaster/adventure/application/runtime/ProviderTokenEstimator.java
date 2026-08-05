package com.dndmaster.adventure.application.runtime;

import java.util.Map;
import java.util.Objects;

public final class ProviderTokenEstimator {
    private final Map<String, Integer> limits;
    public ProviderTokenEstimator(Map<String, Integer> limits) {
        this.limits = Map.copyOf(Objects.requireNonNull(limits));
        if (this.limits.isEmpty() || this.limits.values().stream().anyMatch(value -> value <= 0)) {
            throw new IllegalArgumentException("provider limits must be positive");
        }
    }
    public int limit(String provider) {
        Integer limit = limits.get(provider);
        if (limit == null) throw new IllegalArgumentException("unknown provider: " + provider);
        return limit;
    }
    public ContextUsage usage(String provider, String prompt) {
        Objects.requireNonNull(prompt);
        return new ContextUsage(Math.min(limit(provider), (prompt.length() + 3) / 4), limit(provider));
    }
}
