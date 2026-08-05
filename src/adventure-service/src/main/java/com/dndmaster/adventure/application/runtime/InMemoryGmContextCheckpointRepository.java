package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.checkpoint.GmContextCheckpoint;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryGmContextCheckpointRepository implements GmContextCheckpointRepository {
    private final ConcurrentHashMap<UUID, List<GmContextCheckpoint>> values = new ConcurrentHashMap<>();
    public synchronized void append(GmContextCheckpoint checkpoint) {
        List<GmContextCheckpoint> history = new ArrayList<>(values.getOrDefault(checkpoint.sessionId(), List.of()));
        if (history.stream().anyMatch(item -> item.checkpointId().equals(checkpoint.checkpointId()) || item.sourceTurnId().equals(checkpoint.sourceTurnId()))) return;
        if (!history.isEmpty() && checkpoint.version() != history.get(history.size() - 1).version() + 1) throw new IllegalStateException("checkpoint version conflict");
        history.add(checkpoint); values.put(checkpoint.sessionId(), List.copyOf(history));
    }
    public java.util.Optional<GmContextCheckpoint> current(UUID sessionId) { return history(sessionId).stream().reduce((first, second) -> second); }
    public List<GmContextCheckpoint> history(UUID sessionId) { return values.getOrDefault(sessionId, List.of()); }
}
