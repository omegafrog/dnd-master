package com.dndmaster.character.domain;

public record CharacterSheetData2024(String characterName, int level, boolean heroicInspiration)
        implements CharacterSheetData {
    public CharacterSheetData2024 {
        characterName = CharacterSheetDataValidation.name(characterName);
        CharacterSheetDataValidation.level(level);
    }

    @Override public SheetEdition edition() { return SheetEdition.DND_5E_2024; }
}
