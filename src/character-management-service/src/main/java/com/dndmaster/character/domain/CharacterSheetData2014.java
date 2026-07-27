package com.dndmaster.character.domain;

public record CharacterSheetData2014(String characterName, int level, boolean inspiration, String race, String characterClass, String background, String startingAbilities)
        implements CharacterSheetData {
    public CharacterSheetData2014(String characterName, int level, boolean inspiration) {
        this(characterName, level, inspiration, "", "", "", "");
    }
    public CharacterSheetData2014 {
        characterName = CharacterSheetDataValidation.name(characterName);
        race = value(race); characterClass = value(characterClass); background = value(background); startingAbilities = value(startingAbilities);
        CharacterSheetDataValidation.level(level);
    }

    private static String value(String value) { return value == null ? "" : value.trim(); }

    @Override public SheetEdition edition() { return SheetEdition.DND_5E_2014; }
}
