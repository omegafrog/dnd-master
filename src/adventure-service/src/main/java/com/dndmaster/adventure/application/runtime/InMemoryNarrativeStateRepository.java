package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.narrative.NarrativeState;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryNarrativeStateRepository implements NarrativeStateRepository {
    private final Map<UUID, NarrativeState> states = new ConcurrentHashMap<>();
    @Override public Optional<NarrativeState> findBySessionId(UUID sessionId) { return Optional.ofNullable(states.get(sessionId)); }
    @Override public synchronized void save(UUID sessionId, NarrativeState state) {
        NarrativeState previous = states.get(sessionId);
        if (previous != null && state.version() <= previous.version()) throw new IllegalStateException("state version conflict");
        states.put(sessionId, state);
    }
}
