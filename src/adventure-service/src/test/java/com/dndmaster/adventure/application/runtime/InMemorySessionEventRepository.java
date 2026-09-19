package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.event.SessionEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Test-only session event store. Production wiring uses the database repository. */
public final class InMemorySessionEventRepository implements SessionEventRepository {
    private final ConcurrentHashMap<UUID, List<SessionEvent>> events = new ConcurrentHashMap<>();

    @Override public synchronized void append(SessionEvent event) {
        SessionEvent existingByGlobalId = findById(event.eventId());
        if (existingByGlobalId != null) {
            ensureSameIdentity(existingByGlobalId, event.sessionId(), event.type(), event.payload());
            return;
        }
        List<SessionEvent> current = events.computeIfAbsent(event.sessionId(), ignored -> new ArrayList<>());
        if (current.stream().anyMatch(existing -> existing.version() == event.version())) {
            throw new IllegalStateException("session event version already exists");
        }
        if (!current.isEmpty() && event.version() <= current.stream().mapToLong(SessionEvent::version).max().orElse(-1)) {
            throw new IllegalStateException("session event versions must increase");
        }
        current.add(event);
    }

    @Override public synchronized SessionEvent appendNext(UUID sessionId, UUID eventId, String type, String payload) {
        SessionEvent existingByGlobalId = findById(eventId);
        if (existingByGlobalId != null) {
            ensureSameIdentity(existingByGlobalId, sessionId, type, payload);
            return existingByGlobalId;
        }
        List<SessionEvent> current = events.computeIfAbsent(sessionId, ignored -> new ArrayList<>());
        long nextVersion = current.stream().mapToLong(SessionEvent::version).max().orElse(-1) + 1;
        SessionEvent event = new SessionEvent(sessionId, eventId, nextVersion, type, payload);
        current.add(event);
        return event;
    }

    private static void ensureSameIdentity(SessionEvent existing, UUID sessionId, String type, String payload) {
        if (!existing.sessionId().equals(sessionId) || !existing.type().equals(type)
                || !existing.payload().equals(payload)) {
            throw new SessionEventIdentityConflictException("session event identity conflict");
        }
    }

    private SessionEvent findById(UUID eventId) {
        return events.values().stream().flatMap(List::stream)
                .filter(existing -> existing.eventId().equals(eventId)).findFirst().orElse(null);
    }

    @Override public List<SessionEvent> after(UUID sessionId, long version) {
        return events.getOrDefault(sessionId, List.of()).stream().filter(event -> event.version() > version)
                .sorted(Comparator.comparingLong(SessionEvent::version)).toList();
    }
}
