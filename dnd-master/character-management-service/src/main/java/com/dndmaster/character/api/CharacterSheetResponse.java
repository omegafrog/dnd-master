package com.dndmaster.character.api;

import com.dndmaster.character.domain.CharacterSheet;
import java.util.Objects;
import java.util.UUID;

public record CharacterSheetResponse(
        UUID characterSheetId,
        UUID adventureId,
        String edition,
        String characterName,
        int level,
        boolean inspiration,
        long version) {
    public static CharacterSheetResponse from(CharacterSheet sheet) {
        Objects.requireNonNull(sheet, "character sheet must not be null");
        boolean inspiration = switch (sheet.data()) {
            case com.dndmaster.character.domain.CharacterSheetData2014 data -> data.inspiration();
            case com.dndmaster.character.domain.CharacterSheetData2024 data -> data.heroicInspiration();
        };
        return new CharacterSheetResponse(
                sheet.id().value(), sheet.adventureId().value(), sheet.edition().name(),
                sheet.data().characterName(), sheet.data().level(), inspiration, sheet.version());
    }
}
