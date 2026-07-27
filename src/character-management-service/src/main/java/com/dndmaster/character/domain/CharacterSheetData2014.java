package com.dndmaster.character.domain;

public record CharacterSheetData2014(String characterName, int level, boolean inspiration)
        implements CharacterSheetData {
    public CharacterSheetData2014 {
        characterName = CharacterSheetDataValidation.name(characterName);
        CharacterSheetDataValidation.level(level);
    }

    @Override public SheetEdition edition() { return SheetEdition.DND_5E_2014; }
}
