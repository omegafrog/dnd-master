package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Enforces exact role isolation and approved-active selection at the runtime boundary. */
public final class ApprovedPromptSelectionPolicy {
    public EffectivePromptLineage select(ApprovedPromptConfigurationReadPort port, String role) {
        Objects.requireNonNull(port, "prompt configuration port must not be null");
        String normalized = normalize(role);
        ApprovedPromptConfiguration configuration = port.current(normalized)
                .orElseThrow(() -> new IllegalStateException("no approved active prompt for role " + normalized));
        if (!normalized.equals(configuration.role())) throw new IllegalStateException("prompt role isolation violated");
        return configuration.lineage();
    }

    public List<EffectivePromptLineage> selectAll(ApprovedPromptConfigurationReadPort port, List<String> roles) {
        return roles.stream().map(role -> select(port, role)).toList();
    }

    private static String normalize(String role) {
        if (role == null || role.isBlank()) throw new IllegalArgumentException("prompt role must not be blank");
        return role.trim().toUpperCase(Locale.ROOT);
    }
}
