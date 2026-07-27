package com.dndmaster.adventure.application.progress;

import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import java.util.Objects;

public record ProgressAdventureCommand(AdventureId adventureId, OwnerPlayerId requestingOwner, String action) {
    public ProgressAdventureCommand {
        Objects.requireNonNull(adventureId, "adventure id must not be null");
        Objects.requireNonNull(requestingOwner, "requesting owner must not be null");
        if (action == null || action.isBlank()) throw new IllegalArgumentException("action must not be blank");
        action = action.trim();
    }
}
