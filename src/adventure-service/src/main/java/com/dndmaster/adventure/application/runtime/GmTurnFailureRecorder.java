package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.GmTurn;
import java.util.UUID;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class GmTurnFailureRecorder {
    private final GmTurnRepository turns;
    private final SessionEventRepository events;

    public GmTurnFailureRecorder(GmTurnRepository turns, SessionEventRepository events) {
        this.turns = turns; this.events = events;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(GmTurn turn, UUID adventureId, UUID sessionId, String message, long version) {
        turns.save(turn.process().fail(message == null ? "turn failed" : message), adventureId);
        events.append(new SessionEvent(sessionId, UUID.randomUUID(), version + 1, "GM_TURN_FAILED", message == null ? "turn failed" : message));
    }
}
