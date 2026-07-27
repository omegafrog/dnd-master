package com.dndmaster.character.application;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CharacterSheetsDeletionRequested(UUID sessionId, List<UUID> characterSheetIds) {
    public CharacterSheetsDeletionRequested {
        Objects.requireNonNull(sessionId, "session id must not be null");
        characterSheetIds = List.copyOf(Objects.requireNonNull(characterSheetIds, "character sheet ids must not be null"));
    }
}
