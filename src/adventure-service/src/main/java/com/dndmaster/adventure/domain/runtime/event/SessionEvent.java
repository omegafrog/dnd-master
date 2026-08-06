package com.dndmaster.adventure.domain.runtime.event;

import java.util.Objects;
import java.util.UUID;

public record SessionEvent(UUID sessionId, UUID eventId, long version, String type, String payload) {
    public SessionEvent {
        Objects.requireNonNull(sessionId); Objects.requireNonNull(eventId); Objects.requireNonNull(type); Objects.requireNonNull(payload);
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
    }
}
