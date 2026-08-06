package com.dndmaster.adventure.application.runtime;

import java.util.Objects;

public final class ProviderSwitchPolicy {
    private ProviderSwitchPolicy() { }

    public static ProviderBinding switchProvider(ProviderBinding binding, GmProviderSelection selection,
                                                  boolean turnInProgress) {
        Objects.requireNonNull(binding);
        Objects.requireNonNull(selection);
        if (turnInProgress) throw new IllegalStateException("provider cannot switch during a turn");
        return new ProviderBinding(binding.sessionId(), selection, binding.stateVersion());
    }
}
