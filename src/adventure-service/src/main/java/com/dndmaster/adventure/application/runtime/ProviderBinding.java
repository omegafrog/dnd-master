package com.dndmaster.adventure.application.runtime;

import java.util.Objects;
import java.util.UUID;

public record ProviderBinding(UUID sessionId, GmProviderSelection selection, long stateVersion, boolean turnInProgress) {
    public ProviderBinding(UUID sessionId, GmProviderSelection selection, long stateVersion) {
        this(sessionId, selection, stateVersion, false);
    }
    public ProviderBinding {
        sessionId = Objects.requireNonNull(sessionId);
        selection = Objects.requireNonNull(selection);
        if (stateVersion < 0) throw new IllegalArgumentException("state version must not be negative");
    }
}
