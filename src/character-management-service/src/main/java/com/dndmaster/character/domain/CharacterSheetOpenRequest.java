package com.dndmaster.character.domain;

import java.util.Objects;

public record CharacterSheetOpenRequest(
        AdventureId adventureId, SheetEdition appliedEdition, SheetEdition requestedEdition) {
    public CharacterSheetOpenRequest {
        Objects.requireNonNull(adventureId, "adventure id must not be null");
        Objects.requireNonNull(appliedEdition, "applied edition must not be null");
        Objects.requireNonNull(requestedEdition, "requested edition must not be null");
    }
}
