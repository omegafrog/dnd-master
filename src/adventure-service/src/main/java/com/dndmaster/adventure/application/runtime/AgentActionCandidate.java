package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import java.util.Objects;
import java.util.UUID;

/** AI proposal. Runtime saga remains the authority that applies it. */
public record AgentActionCandidate(UUID turnId, UUID commandId, CharacterSheetId characterSheetId, String action) {
    public AgentActionCandidate {
        Objects.requireNonNull(turnId, "turn id must not be null");
        Objects.requireNonNull(commandId, "command id must not be null");
        Objects.requireNonNull(characterSheetId, "character sheet id must not be null");
        if (action == null || action.isBlank()) throw new IllegalArgumentException("agent action must not be blank");
        action = action.trim();
    }
}
