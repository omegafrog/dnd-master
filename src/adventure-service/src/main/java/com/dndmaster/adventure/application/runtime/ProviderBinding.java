package com.dndmaster.adventure.application.runtime;

import java.util.Objects;
import java.util.UUID;

public record ProviderBinding(UUID sessionId, GmProviderSelection selection, long stateVersion) {
    public ProviderBinding {
        sessionId = Objects.requireNonNull(sessionId);
        selection = Objects.requireNonNull(selection);
        if (stateVersion < 0) throw new IllegalArgumentException("state version must not be negative");
    }
}
