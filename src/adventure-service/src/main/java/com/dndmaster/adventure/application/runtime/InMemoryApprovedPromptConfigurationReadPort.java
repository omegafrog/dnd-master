package com.dndmaster.adventure.application.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Test/local adapter with role isolation and monotonic activation versions. */
public final class InMemoryApprovedPromptConfigurationReadPort implements ApprovedPromptConfigurationReadPort {
    private final Map<String, ApprovedPromptConfiguration> configurations = new LinkedHashMap<>();

    public synchronized void activate(ApprovedPromptConfiguration configuration) {
        String role = configuration.role();
        ApprovedPromptConfiguration current = configurations.get(role);
        if (current != null && configuration.activationVersion() <= current.activationVersion()) {
            throw new IllegalStateException("stale prompt activation for role " + role);
        }
        configurations.put(role, configuration);
    }

    @Override public synchronized Optional<ApprovedPromptConfiguration> current(String role) {
        if (role == null || role.isBlank()) throw new IllegalArgumentException("prompt role must not be blank");
        return Optional.ofNullable(configurations.get(role.trim().toUpperCase(java.util.Locale.ROOT)));
    }
}
