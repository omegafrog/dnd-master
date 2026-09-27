package com.dndmaster.adventure.application.runtime;

public record ConversationCompactionCandidate(long sourceStart, long sourceEnd, long expectedAdventureVersion, String text) {
    public ConversationCompactionCandidate {
        if (sourceStart < 0 || sourceEnd < sourceStart || expectedAdventureVersion < 0 || text == null || text.isBlank()) throw new IllegalArgumentException("invalid compaction candidate");
        text = text.trim();
    }
}
