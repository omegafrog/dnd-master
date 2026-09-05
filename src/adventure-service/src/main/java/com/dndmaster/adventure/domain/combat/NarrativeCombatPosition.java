package com.dndmaster.adventure.domain.combat;

import java.util.Objects;
import java.util.UUID;

/** Structured relative position used when an encounter has no tactical map. */
public record NarrativeCombatPosition(UUID subjectId, UUID targetId, String rangeBand, String cover) {
    public NarrativeCombatPosition {
        Objects.requireNonNull(subjectId, "position subject must not be null");
        Objects.requireNonNull(targetId, "position target must not be null");
        if (rangeBand == null || rangeBand.isBlank()) throw new IllegalArgumentException("range band is required");
        if (cover == null || cover.isBlank()) throw new IllegalArgumentException("cover is required");
        rangeBand = rangeBand.trim().toUpperCase();
        cover = cover.trim().toUpperCase();
    }
}
