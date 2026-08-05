package com.dndmaster.adventure.application.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemorySessionEventRepository implements SessionEventRepository {
    private final ConcurrentHashMap<UUID, List<SessionEvent>> events = new ConcurrentHashMap<>();

    @Override public synchronized void append(SessionEvent event) {
        List<SessionEvent> current = events.computeIfAbsent(event.sessionId(), ignored -> new ArrayList<>());
        if (current.stream().anyMatch(existing -> existing.eventId().equals(event.eventId()))) return;
        if (current.stream().anyMatch(existing -> existing.version() == event.version())) {
            throw new IllegalStateException("session event version already exists");
        }
        if (!current.isEmpty() && event.version() <= current.stream().mapToLong(SessionEvent::version).max().orElse(-1)) {
            throw new IllegalStateException("session event versions must increase");
        }
        current.add(event);
    }

    @Override public List<SessionEvent> after(UUID sessionId, long version) {
        return events.getOrDefault(sessionId, List.of()).stream().filter(event -> event.version() > version)
                .sorted(Comparator.comparingLong(SessionEvent::version)).toList();
    }
}
