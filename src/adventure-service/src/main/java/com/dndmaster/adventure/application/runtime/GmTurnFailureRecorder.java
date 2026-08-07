package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.event.SessionEvent;
import com.dndmaster.adventure.domain.runtime.GmTurn;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class GmTurnFailureRecorder {
    private final GmTurnRepository turns;
    private final SessionEventRepository events;

    public GmTurnFailureRecorder(GmTurnRepository turns, SessionEventRepository events) {
        this.turns = turns; this.events = events;
    }

    @Transactional
    public void record(GmTurn turn, UUID adventureId, UUID sessionId, String message, long version) {
        GmTurn failed = turn.status() == com.dndmaster.adventure.domain.runtime.GmTurnStatus.PROCESSING
                ? turn.fail(message == null ? "turn failed" : message)
                : turn.process().fail(message == null ? "turn failed" : message);
        turns.save(failed, adventureId);
        events.append(new SessionEvent(sessionId, UUID.randomUUID(), version + 1, "GM_TURN_FAILED", message == null ? "turn failed" : message));
    }
}
