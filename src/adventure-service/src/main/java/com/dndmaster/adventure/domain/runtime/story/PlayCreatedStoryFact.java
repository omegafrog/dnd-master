package com.dndmaster.adventure.domain.runtime.story;

import java.util.Objects;
import java.util.UUID;

/** A durable fact established by play, distinct from scenario-source truth. */
public record PlayCreatedStoryFact(UUID factId, String content, UUID establishedTurnId) {
    public PlayCreatedStoryFact {
        Objects.requireNonNull(factId, "story fact id must not be null");
        Objects.requireNonNull(establishedTurnId, "established turn id must not be null");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("story fact content must not be blank");
        content = content.trim();
    }
}
