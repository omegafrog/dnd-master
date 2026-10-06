package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.Objects;

public record ConversationCompactionCandidate(long sourceStart, long sourceEnd, long expectedAdventureVersion,
                                              String summary, List<LongTermFactCandidate> longTermFacts) {
    public ConversationCompactionCandidate {
        if (sourceStart < 0 || sourceEnd < sourceStart || expectedAdventureVersion < 0) throw new IllegalArgumentException("invalid compaction candidate");
        summary = Objects.requireNonNull(summary, "summary is required");
        longTermFacts = List.copyOf(Objects.requireNonNull(longTermFacts, "long-term facts are required"));
    }
    public ConversationCompactionCandidate(long sourceStart, long sourceEnd, long expectedAdventureVersion, String summary) {
        this(sourceStart, sourceEnd, expectedAdventureVersion, summary, List.of());
    }
    public ConversationCompactionCandidate(long sourceStart, long sourceEnd, long expectedAdventureVersion,
                                           List<SourceExcerpt> excerpts) {
        this(sourceStart, sourceEnd, expectedAdventureVersion, renderLegacy(excerpts), List.of());
    }
    public ConversationCompactionCandidate(long sourceStart, long sourceEnd, long expectedAdventureVersion,
                                           List<SourceExcerpt> excerpts, List<LongTermFactCandidate> facts) {
        this(sourceStart, sourceEnd, expectedAdventureVersion, renderLegacy(excerpts), facts);
    }
    private static String renderLegacy(List<SourceExcerpt> excerpts) {
        return Objects.requireNonNull(excerpts, "source excerpts are required").stream()
                .map(excerpt -> excerpt.speaker() + ": " + excerpt.text()).collect(java.util.stream.Collectors.joining(" "));
    }

    public record SourceExcerpt(long sequence, String speaker, String text) {
        public SourceExcerpt(long sequence, String text) { this(sequence, "UNKNOWN", text); }
        public SourceExcerpt {
            if (sequence < 0 || speaker == null || speaker.isBlank() || text == null || text.isBlank()) throw new IllegalArgumentException("invalid source excerpt");
        }
    }
}
