package com.dndmaster.adventure.application.runtime;

import java.util.UUID;

public record RuntimeCommandJournalEntry(UUID commandId, UUID sessionId, UUID turnId, UUID ownerPlayerId,
                                         String toolName, String fingerprint, RuntimeCommandStatus status,
                                         RuntimeCommandOutcome outcome, long version) {
    public RuntimeCommandJournalEntry with(RuntimeCommandStatus next, RuntimeCommandOutcome result) {
        return new RuntimeCommandJournalEntry(commandId, sessionId, turnId, ownerPlayerId, toolName, fingerprint, next, result, version + 1);
    }
}
