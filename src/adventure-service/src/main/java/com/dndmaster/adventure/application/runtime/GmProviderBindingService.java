package com.dndmaster.adventure.application.runtime;

import java.util.Objects;
import java.util.UUID;

public final class GmProviderBindingService {
    private final GmProviderBindingRepository repository;

    public GmProviderBindingService(GmProviderBindingRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public synchronized ProviderBinding currentOrInitialize(UUID sessionId, GmProviderSelection defaultSelection) {
        return repository.current(sessionId).orElseGet(() -> {
            ProviderBinding initial = new ProviderBinding(sessionId, defaultSelection, 0, false);
            repository.save(initial);
            return initial;
        });
    }

    public ProviderBinding switchProvider(UUID sessionId, long expectedVersion, GmProviderSelection selection) {
        ProviderBinding current = current(sessionId);
        checkVersion(current, expectedVersion);
        if (current.turnInProgress()) throw new IllegalStateException("provider cannot switch during a turn");
        ProviderBinding updated = new ProviderBinding(sessionId, selection, expectedVersion + 1, false);
        if (!repository.compareAndSet(sessionId, expectedVersion, updated)) throw new IllegalStateException("provider binding changed");
        return updated;
    }

    public ProviderBinding beginTurn(UUID sessionId, long expectedVersion) {
        ProviderBinding current = current(sessionId);
        checkVersion(current, expectedVersion);
        if (current.turnInProgress()) throw new IllegalStateException("turn already in progress");
        return update(current, expectedVersion, true);
    }

    public ProviderBinding completeTurn(UUID sessionId, long expectedVersion) {
        ProviderBinding current = current(sessionId);
        checkVersion(current, expectedVersion);
        if (!current.turnInProgress()) throw new IllegalStateException("turn is not in progress");
        return update(current, expectedVersion, false);
    }

    private ProviderBinding update(ProviderBinding current, long expectedVersion, boolean active) {
        ProviderBinding updated = new ProviderBinding(current.sessionId(), current.selection(), expectedVersion + 1, active);
        if (!repository.compareAndSet(current.sessionId(), expectedVersion, updated)) throw new IllegalStateException("provider binding changed");
        return updated;
    }

    private ProviderBinding current(UUID sessionId) { return repository.current(sessionId).orElseThrow(() -> new IllegalStateException("provider binding not found")); }
    private static void checkVersion(ProviderBinding binding, long expectedVersion) {
        if (binding.stateVersion() != expectedVersion) throw new IllegalStateException("provider binding version mismatch");
    }
}
