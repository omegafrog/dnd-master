package com.dndmaster.character.domain;

public record CharacterSheetData2014(String characterName, int level, boolean inspiration, String race, String characterClass, String background, String startingAbilities, String derivedStatistics, String characterBuild, String characterState)
        implements CharacterSheetData {
    public CharacterSheetData2014(String characterName, int level, boolean inspiration) {
        this(characterName, level, inspiration, "", "", "", "", "", "", "");
    }
    public CharacterSheetData2014(String characterName, int level, boolean inspiration, String race, String characterClass, String background, String startingAbilities) {
        this(characterName, level, inspiration, race, characterClass, background, startingAbilities, "", "", "");
    }
    public CharacterSheetData2014(String characterName, int level, boolean inspiration, String race, String characterClass, String background, String startingAbilities, String derivedStatistics) { this(characterName, level, inspiration, race, characterClass, background, startingAbilities, derivedStatistics, "", ""); }
    public CharacterSheetData2014 {
        characterName = CharacterSheetDataValidation.name(characterName);
        race = value(race); characterClass = value(characterClass); background = value(background); startingAbilities = value(startingAbilities); derivedStatistics = value(derivedStatistics); characterBuild = value(characterBuild); characterState = value(characterState);
        CharacterSheetDataValidation.level(level);
    }

    private static String value(String value) { return value == null ? "" : value.trim(); }

    @Override public SheetEdition edition() { return SheetEdition.DND_5E_2014; }
}
