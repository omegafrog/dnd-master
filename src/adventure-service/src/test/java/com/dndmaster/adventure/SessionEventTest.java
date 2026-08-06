package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.application.runtime.InMemorySessionEventRepository;
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
}
