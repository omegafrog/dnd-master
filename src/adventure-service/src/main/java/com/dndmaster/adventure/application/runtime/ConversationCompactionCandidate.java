package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.Objects;

public record ConversationCompactionCandidate(long sourceStart, long sourceEnd, long expectedAdventureVersion,
                                              String text, List<Long> referencedSequences) {
    public ConversationCompactionCandidate {
        if (sourceStart < 0 || sourceEnd < sourceStart || expectedAdventureVersion < 0 || text == null || text.isBlank()) throw new IllegalArgumentException("invalid compaction candidate");
        text = text.trim();
        referencedSequences = List.copyOf(Objects.requireNonNull(referencedSequences, "source references are required"));
    }
}
