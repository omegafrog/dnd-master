package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.application.combat.MovementFollowUpCommand;
import com.dndmaster.adventure.domain.runtime.event.SessionEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

/** Publishes movement follow-ups with a session-local cursor and stable identity. */
public final class MovementFollowUpEventPublisher {
    private static final int MAX_APPEND_ATTEMPTS = 8;
    private final SessionEventRepository events;
    private final ObjectMapper objectMapper;

    public MovementFollowUpEventPublisher(SessionEventRepository events, ObjectMapper objectMapper) {
        this.events = Objects.requireNonNull(events, "session event repository must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "object mapper must not be null");
    }

    public synchronized MovementFollowUpPort.Result publish(MovementFollowUpCommand command, UUID adventureId,
            UUID sessionId, UUID ownerPlayerId) {
        Objects.requireNonNull(command, "movement follow-up command must not be null");
        Objects.requireNonNull(adventureId, "adventure id must not be null");
        Objects.requireNonNull(sessionId, "session id must not be null");
        Objects.requireNonNull(ownerPlayerId, "owner player id must not be null");
        final String payload;
        try {
            payload = objectMapper.writeValueAsString(command);
        } catch (IOException failure) {
            return MovementFollowUpPort.Result.retry(failure.getMessage());
        }
        for (int attempt = 0; attempt < MAX_APPEND_ATTEMPTS; attempt++) {
            var existing = events.after(sessionId, -1).stream()
                    .filter(event -> event.eventId().equals(command.commandId())).findFirst();
            if (existing.isPresent()) return MovementFollowUpPort.Result.done(command.kind().name());
            long nextVersion = events.after(sessionId, -1).stream()
                    .mapToLong(SessionEvent::version).max().orElse(-1) + 1;
            try {
                events.append(new SessionEvent(sessionId, command.commandId(), nextVersion,
                        "MOVEMENT_FOLLOW_UP", payload));
                return MovementFollowUpPort.Result.done(command.kind().name());
            } catch (RuntimeException conflict) {
                if (attempt == MAX_APPEND_ATTEMPTS - 1) return MovementFollowUpPort.Result.retry(conflict.getMessage());
            }
        }
        return MovementFollowUpPort.Result.retry("movement follow-up event append exhausted retries");
    }
}
