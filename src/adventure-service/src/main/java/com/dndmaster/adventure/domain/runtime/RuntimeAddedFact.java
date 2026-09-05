package com.dndmaster.adventure.domain.runtime;

import java.util.Objects;
import java.util.UUID;

/** A fact established during this playthrough when source lookup found no answer. */
public record RuntimeAddedFact(UUID factId, String content, UUID establishedTurnId) {
    public RuntimeAddedFact {
        Objects.requireNonNull(factId, "runtime fact id must not be null");
        Objects.requireNonNull(establishedTurnId, "established turn id must not be null");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("runtime fact content must not be blank");
        content = content.trim();
    }
}
