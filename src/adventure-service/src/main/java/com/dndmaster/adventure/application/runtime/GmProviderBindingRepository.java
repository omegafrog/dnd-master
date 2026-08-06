package com.dndmaster.adventure.application.runtime;

import java.util.Optional;
import java.util.UUID;

public interface GmProviderBindingRepository {
    Optional<ProviderBinding> current(UUID sessionId);
    void save(ProviderBinding binding);
    boolean compareAndSet(UUID sessionId, long expectedVersion, ProviderBinding updated);
}
