package com.dndmaster.adventure.application.runtime;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryRuntimeCommandJournal implements RuntimeCommandJournal {
    private final ConcurrentHashMap<UUID, RuntimeCommandJournalEntry> entries = new ConcurrentHashMap<>();
    public Optional<RuntimeCommandJournalEntry> find(UUID commandId) { return Optional.ofNullable(entries.get(commandId)); }
    public synchronized boolean claim(RuntimeCommandJournalEntry pending) {
        return entries.putIfAbsent(pending.commandId(), pending) == null;
    }
    public void record(RuntimeCommandJournalEntry entry) { entries.put(entry.commandId(), entry); }
}
