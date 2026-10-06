package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.AdventureId;
import java.util.Objects;
import java.util.UUID;

/** Adventure-local lookup record; the referenced RuntimeAddedFact remains authoritative. */
public record LongTermAdventureFact(AdventureId adventureId, UUID factId, UUID establishedTurnId,
                                    long sourceAdventureVersion, String kind, String relevance,
                                    boolean playerVisible, long version) {
    public LongTermAdventureFact {
        adventureId = Objects.requireNonNull(adventureId, "adventure id is required");
        factId = Objects.requireNonNull(factId, "fact id is required");
        establishedTurnId = Objects.requireNonNull(establishedTurnId, "established turn id is required");
        if (sourceAdventureVersion < 0 || version < 1) throw new IllegalArgumentException("invalid long-term fact version");
        kind = Objects.requireNonNull(kind, "fact kind is required").trim();
        relevance = Objects.requireNonNull(relevance, "fact relevance is required").trim();
        if (kind.isBlank() || relevance.isBlank()) throw new IllegalArgumentException("long-term fact values must not be blank");
    }
}
