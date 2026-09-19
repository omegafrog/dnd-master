package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.application.runtime.InMemorySessionEventRepository;
import com.dndmaster.adventure.application.runtime.MovementFollowUpEventPublisher;
import com.dndmaster.adventure.application.runtime.MovementFollowUpPort;
import com.dndmaster.adventure.application.runtime.SessionEventRepository;
import com.dndmaster.adventure.application.combat.MovementFollowUpCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import com.dndmaster.adventure.domain.runtime.event.SessionEvent;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionEventTest {
    @Test
    void event_versions_are_monotonic_and_duplicate_safe() {
        InMemorySessionEventRepository events = new InMemorySessionEventRepository();
        UUID session = UUID.randomUUID();
        SessionEvent event = new SessionEvent(session, UUID.randomUUID(), 7, "GM_TURN_COMMITTED", "result");
        events.append(event);
        events.append(event);
        assertEquals(1, events.after(session, 0).size());
        assertEquals(0, events.after(session, 7).size());
    }

    @Test
    void movement_follow_ups_allocate_monotonic_versions_and_retry_conflicts_without_loss() {
        InMemorySessionEventRepository events = new InMemorySessionEventRepository();
        MovementFollowUpEventPublisher publisher = new MovementFollowUpEventPublisher(events, new ObjectMapper());
        UUID session = UUID.randomUUID();
        UUID adventure = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        MovementFollowUpCommand first = MovementFollowUpCommand.hostileObserved(UUID.randomUUID());
        MovementFollowUpCommand second = new MovementFollowUpCommand(UUID.randomUUID(), UUID.randomUUID(),
                MovementFollowUpCommand.Kind.WARNING, "FEATURE_REVEALED");

        assertEquals("COMBAT", publisher.publish(first, adventure, session, owner).value());
        assertEquals("WARNING", publisher.publish(second, adventure, session, owner).value());
        assertEquals("COMBAT", publisher.publish(first, adventure, session, owner).value());
        assertEquals(List.of(0L, 1L), events.after(session, -1).stream().map(SessionEvent::version).toList());
        assertEquals(List.of(first.commandId(), second.commandId()),
                events.after(session, -1).stream().map(SessionEvent::eventId).toList());
    }

    @Test
    void movement_follow_up_recalculates_the_version_after_a_conflict() {
        InMemorySessionEventRepository stored = new InMemorySessionEventRepository();
        SessionEventRepository conflicting = new SessionEventRepository() {
            private boolean conflict = true;
            @Override public void append(SessionEvent event) {
                if (conflict) {
                    conflict = false;
                    stored.append(new SessionEvent(event.sessionId(), UUID.randomUUID(), event.version(),
                            "OTHER_EVENT", "other"));
                }
                stored.append(event);
            }
            @Override public List<SessionEvent> after(UUID sessionId, long version) { return stored.after(sessionId, version); }
        };
        MovementFollowUpCommand followUp = MovementFollowUpCommand.hostileObserved(UUID.randomUUID());

        MovementFollowUpPort.Result result = new MovementFollowUpEventPublisher(conflicting, new ObjectMapper()).publish(
                followUp, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        assertEquals(MovementFollowUpPort.Result.Status.DONE, result.status());
    }
}
