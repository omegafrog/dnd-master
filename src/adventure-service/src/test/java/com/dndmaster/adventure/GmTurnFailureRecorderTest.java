package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.application.runtime.GmTurnFailureRecorder;
import com.dndmaster.adventure.application.runtime.GmTurnRepository;
import com.dndmaster.adventure.domain.runtime.event.SessionEvent;
import com.dndmaster.adventure.application.runtime.SessionEventRepository;
import com.dndmaster.adventure.domain.runtime.GmInput;
import com.dndmaster.adventure.domain.runtime.GmTurn;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GmTurnFailureRecorderTest {
    @Test
    void records_terminal_failure_and_failure_event_after_runtime_rollback() {
        RecordingTurns turns = new RecordingTurns();
        RecordingEvents events = new RecordingEvents();
        GmTurn turn = GmTurn.start(UUID.randomUUID(), UUID.randomUUID(), 4, new GmInput.TextInput("look"));

        new GmTurnFailureRecorder(turns, events).record(turn, UUID.randomUUID(), UUID.randomUUID(), "provider failed", 4);

        assertEquals("FAILED", turns.saved.status().name());
        assertEquals("GM_TURN_FAILED", events.saved.type());
        assertEquals("provider failed", events.saved.payload());
        assertEquals(5, events.saved.version());
        assertEquals("provider failed", turns.recoveredFailure);
    }

    private static final class RecordingTurns implements GmTurnRepository {
        GmTurn saved;
        String recoveredFailure;
        @Override public Optional<GmTurn> findByCommandId(UUID commandId) { return Optional.empty(); }
        @Override public void recoverActive(UUID adventureId, String failure) { recoveredFailure = failure; }
        @Override public void save(GmTurn turn, UUID adventureId) { saved = turn; }
    }

    private static final class RecordingEvents implements SessionEventRepository {
        SessionEvent saved;
        @Override public void append(SessionEvent event) { saved = event; }
        @Override public List<SessionEvent> after(UUID sessionId, long version) { return new ArrayList<>(); }
    }
}
