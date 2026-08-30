package com.dndmaster.adventure.application.runtime;

import java.util.Map;
import java.util.Optional;

/** Deployment adapter for the approved gm-eval projection supplied at the runtime boundary. */
public final class EnvironmentApprovedPromptConfigurationReadPort implements ApprovedPromptConfigurationReadPort {
    private final Map<String, ApprovedPromptConfiguration> configurations;

    public EnvironmentApprovedPromptConfigurationReadPort(Map<String, ApprovedPromptConfiguration> configurations) {
        this.configurations = Map.copyOf(configurations == null ? Map.of() : configurations);
    }

    @Override public Optional<ApprovedPromptConfiguration> current(String role) {
        if (role == null || role.isBlank()) throw new IllegalArgumentException("prompt role must not be blank");
        return Optional.ofNullable(configurations.get(role.trim().toUpperCase(java.util.Locale.ROOT)));
    }
}
