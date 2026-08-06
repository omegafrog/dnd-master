package com.dndmaster.adventure.application.runtime;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryGmProviderBindingRepository implements GmProviderBindingRepository {
    private final Map<UUID, ProviderBinding> bindings = new ConcurrentHashMap<>();

    @Override public Optional<ProviderBinding> current(UUID sessionId) { return Optional.ofNullable(bindings.get(sessionId)); }
    @Override public synchronized void save(ProviderBinding binding) { bindings.put(binding.sessionId(), binding); }
    @Override public synchronized boolean compareAndSet(UUID sessionId, long expectedVersion, ProviderBinding updated) {
        ProviderBinding current = bindings.get(sessionId);
        if (current == null || current.stateVersion() != expectedVersion) return false;
        bindings.put(sessionId, updated);
        return true;
    }
}
