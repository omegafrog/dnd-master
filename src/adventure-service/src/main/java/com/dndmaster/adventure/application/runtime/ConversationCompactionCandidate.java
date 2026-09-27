package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.Objects;

public record ConversationCompactionCandidate(long sourceStart, long sourceEnd, long expectedAdventureVersion,
                                              List<SourceExcerpt> excerpts) {
    public ConversationCompactionCandidate {
        if (sourceStart < 0 || sourceEnd < sourceStart || expectedAdventureVersion < 0) throw new IllegalArgumentException("invalid compaction candidate");
        excerpts = List.copyOf(Objects.requireNonNull(excerpts, "source excerpts are required"));
    }

    public record SourceExcerpt(long sequence, String speaker, String text) {
        public SourceExcerpt(long sequence, String text) { this(sequence, "UNKNOWN", text); }
        public SourceExcerpt {
            if (sequence < 0 || speaker == null || speaker.isBlank() || text == null || text.isBlank()) throw new IllegalArgumentException("invalid source excerpt");
        }
    }
}
