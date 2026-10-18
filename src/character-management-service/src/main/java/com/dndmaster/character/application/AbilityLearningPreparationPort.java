package com.dndmaster.character.application;

import com.dndmaster.character.domain.CharacterSheetId;
import com.dndmaster.character.domain.SessionId;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;

@FunctionalInterface
public interface AbilityLearningPreparationPort {
    void prepare(UUID adventureId, SessionId sessionId, UUID ownerPlayerId, CharacterSheetId sheetId,
            List<String> abilityIds);

    default void prepare(UUID adventureId, SessionId sessionId, UUID ownerPlayerId, CharacterSheetId sheetId,
            List<String> abilityIds, JsonNode sourceDefinition) {
        if (sourceDefinition != null && !sourceDefinition.isNull())
            throw new IllegalArgumentException("source definition input is not supported by this preparation gateway");
        prepare(adventureId, sessionId, ownerPlayerId, sheetId, abilityIds);
    }

    default void activate(UUID adventureId, SessionId sessionId, UUID ownerPlayerId, CharacterSheetId sheetId,
            List<String> abilityIds) {}
}
