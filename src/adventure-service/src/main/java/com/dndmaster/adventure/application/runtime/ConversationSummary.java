package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.AdventureId;
import java.util.Objects;

/** An immutable summary; its source originals remain in Adventure conversation storage. */
public record ConversationSummary(AdventureId adventureId, long version, long sourceStart, long sourceEnd,
                                  long sourceAdventureVersion, String text) {
    public ConversationSummary {
        adventureId = Objects.requireNonNull(adventureId);
        if (version < 1 || sourceStart < 0 || sourceEnd < sourceStart || sourceAdventureVersion < 0) throw new IllegalArgumentException("invalid summary provenance");
        if (text == null || text.isBlank()) throw new IllegalArgumentException("summary text must not be blank");
        text = text.trim();
    }
}
