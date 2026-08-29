package com.dndmaster.adventure.domain.runtime.narrative;

import java.util.Set;

public record CharacterKnowledge(String actorId, Set<String> factIds, Set<Belief> beliefs) {
    public CharacterKnowledge {
        if (actorId == null || actorId.isBlank()) throw new IllegalArgumentException("character id must not be blank");
        actorId = actorId.trim(); factIds = Set.copyOf(factIds == null ? Set.of() : factIds);
        beliefs = Set.copyOf(beliefs == null ? Set.of() : beliefs);
    }
}
