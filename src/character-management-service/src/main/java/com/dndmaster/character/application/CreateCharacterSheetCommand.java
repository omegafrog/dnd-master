package com.dndmaster.character.application;

import com.dndmaster.character.domain.AdventureId;
import com.dndmaster.character.domain.SessionId;
import com.dndmaster.character.domain.CharacterSheetData;
import com.dndmaster.character.domain.SheetEdition;
import java.util.Objects;

public record CreateCharacterSheetCommand(
        SessionId sessionId, SheetEdition requestedEdition, CharacterSheetData data) {
    public CreateCharacterSheetCommand {
        Objects.requireNonNull(sessionId, "session id must not be null");
        Objects.requireNonNull(requestedEdition, "requested edition must not be null");
        Objects.requireNonNull(data, "data must not be null");
    }

    public CreateCharacterSheetCommand(AdventureId adventureId, SheetEdition edition, CharacterSheetData data) {
        this(new SessionId(adventureId.value()), edition, data);
    }

    public AdventureId adventureId() { return sessionId.asAdventureId(); }
}
