package com.dndmaster.adventure.application.runtime;

import java.util.UUID;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Short, committed lock transactions surrounding the external GM call. */
public class RuntimeTurnLockService {
    private final GmProviderBindingRepository repository;
    public RuntimeTurnLockService(GmProviderBindingRepository repository) { this.repository = repository; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void acquire(UUID sessionId, UUID turnId) {
        ProviderBinding current = repository.current(sessionId).orElseThrow(() -> new IllegalStateException("provider binding not found"));
        if (current.turnInProgress()) throw new GmTurnAlreadyInProgressException(sessionId, turnId);
        ProviderBinding locked = new ProviderBinding(sessionId, current.selection(), current.stateVersion() + 1, true);
        if (!repository.compareAndSet(sessionId, current.stateVersion(), locked)) {
            throw new GmTurnAlreadyInProgressException(sessionId, turnId);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(UUID sessionId) {
        ProviderBinding current = repository.current(sessionId).orElse(null);
        if (current == null || !current.turnInProgress()) return;
        ProviderBinding released = new ProviderBinding(sessionId, current.selection(), current.stateVersion() + 1, false);
        repository.compareAndSet(sessionId, current.stateVersion(), released);
    }
}
