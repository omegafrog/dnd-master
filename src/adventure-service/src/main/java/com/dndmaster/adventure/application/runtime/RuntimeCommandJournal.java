package com.dndmaster.adventure.application.runtime;

import java.util.Optional;
import java.util.UUID;

public interface RuntimeCommandJournal {
    Optional<RuntimeCommandJournalEntry> find(UUID commandId);
    void record(RuntimeCommandJournalEntry entry);
}
