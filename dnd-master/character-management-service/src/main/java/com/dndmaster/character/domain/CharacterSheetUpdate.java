package com.dndmaster.character.domain;

import java.util.Objects;

public record CharacterSheetUpdate(
        SheetEdition edition, CharacterSheetData data, InputMode inputMode) {
    public CharacterSheetUpdate {
        Objects.requireNonNull(edition, "edition must not be null");
        Objects.requireNonNull(data, "data must not be null");
        Objects.requireNonNull(inputMode, "input mode must not be null");
    }
}
