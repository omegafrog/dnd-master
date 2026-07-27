package com.dndmaster.character.application;

import com.dndmaster.character.domain.AdventureId;
import com.dndmaster.character.domain.SessionId;
import com.dndmaster.character.domain.CharacterSheetData;
import com.dndmaster.character.domain.SheetEdition;
import java.util.Objects;
import java.util.UUID;

public record CreateCharacterSheetCommand(
        SessionId sessionId, UUID ownerPlayerId, SheetEdition requestedEdition, CharacterSheetData data) {
    public CreateCharacterSheetCommand {
        Objects.requireNonNull(sessionId, "session id must not be null");
        Objects.requireNonNull(requestedEdition, "requested edition must not be null");
        Objects.requireNonNull(data, "data must not be null");
    }

    public CreateCharacterSheetCommand(SessionId sessionId, SheetEdition edition, CharacterSheetData data) { this(sessionId, null, edition, data); }

    public CreateCharacterSheetCommand(AdventureId adventureId, SheetEdition edition, CharacterSheetData data) {
        this(new SessionId(adventureId.value()), null, edition, data);
    }

    public AdventureId adventureId() { return sessionId.asAdventureId(); }
}
