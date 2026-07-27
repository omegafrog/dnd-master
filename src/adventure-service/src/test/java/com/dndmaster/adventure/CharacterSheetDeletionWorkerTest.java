package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.dndmaster.adventure.application.session.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class CharacterSheetDeletionWorkerTest {
    @Test
    void failed_delivery_is_marked_for_retry_then_completes() {
        UUID eventId = UUID.randomUUID();
        FakeOutbox outbox = new FakeOutbox(eventId);
        int[] calls = {0};
        CharacterSheetDeletionWorker worker = new CharacterSheetDeletionWorker(outbox, (session, ids) -> {
            if (++calls[0] == 1) throw new IllegalStateException("character service unavailable");
        });

        worker.process();
        assertEquals(1, outbox.failures);
        worker.process();
        assertEquals(1, outbox.completions);
    }

    private static final class FakeOutbox implements AdventureSessionStartOutboxRepository {
        final UUID eventId; int attempts; int failures; int completions;
        FakeOutbox(UUID eventId) { this.eventId = eventId; }
        public void prepare(com.dndmaster.adventure.domain.adventure.SessionId s, UUID r, UUID a, UUID p) {}
        public void commit(com.dndmaster.adventure.domain.adventure.SessionId s, UUID r) {}
        public Optional<DeletionEvent> claimNextDeletion() { return attempts++ < 2 ? Optional.of(new DeletionEvent(eventId, UUID.randomUUID(), List.of(UUID.randomUUID()))) : Optional.empty(); }
        public void completeDeletion(UUID id) { completions++; }
        public void failDeletion(UUID id, String reason) { failures++; }
    }
}
