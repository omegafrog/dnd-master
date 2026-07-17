package com.dndmaster.character.application;

import com.dndmaster.character.domain.AdventureId;
import com.dndmaster.character.domain.CharacterSheetData;
import com.dndmaster.character.domain.SheetEdition;
import java.util.Objects;

public record CreateCharacterSheetCommand(
        AdventureId adventureId, SheetEdition requestedEdition, CharacterSheetData data) {
    public CreateCharacterSheetCommand {
        Objects.requireNonNull(adventureId, "adventure id must not be null");
        Objects.requireNonNull(requestedEdition, "requested edition must not be null");
        Objects.requireNonNull(data, "data must not be null");
    }
}
