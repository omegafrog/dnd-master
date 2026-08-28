package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.event.SessionEvent;
import com.dndmaster.adventure.domain.runtime.GmTurn;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

public class GmTurnFailureRecorder {
    private final GmTurnRepository turns;
    private final SessionEventRepository events;

    public GmTurnFailureRecorder(GmTurnRepository turns, SessionEventRepository events) {
        this.turns = turns; this.events = events;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(GmTurn turn, UUID adventureId, UUID sessionId, String message, long version) {
        String safeFailure = safeFailure(message);
        turns.save(turn.process().failRetryable(safeFailure), adventureId);
        events.append(new SessionEvent(sessionId, UUID.randomUUID(), version + 1, "GM_TURN_FAILED", safeFailure));
    }

    private static String safeFailure(String message) {
        if (message == null || message.isBlank()) return "GM_TURN_FAILED_RETRYABLE";
        String normalized = message.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("provider") || normalized.contains("timeout")) return "GM_PROVIDER_UNAVAILABLE";
        if (normalized.contains("candidate") || normalized.contains("citation")
                || normalized.contains("narration") || normalized.contains("judgment")) return "GM_CANDIDATE_INVALID";
        if (normalized.contains("version") || normalized.contains("conflict")) return "GM_TURN_CONFLICT";
        return "GM_TURN_FAILED_RETRYABLE";
    }
}
