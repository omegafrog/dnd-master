package com.dndmaster.adventure.application.runtime;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

public interface RuntimeCommandJournal {
    Optional<RuntimeCommandJournalEntry> find(UUID commandId);
    boolean claim(RuntimeCommandJournalEntry pending);
    void record(RuntimeCommandJournalEntry entry);
    default boolean markUnknownIfStale(UUID commandId, Instant staleBefore) { return false; }
}
