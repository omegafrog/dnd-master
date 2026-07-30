package com.dndmaster.character.domain;

public record CharacterSheetData2024(String characterName, int level, boolean heroicInspiration, String race, String characterClass, String background, String startingAbilities, String derivedStatistics, String characterBuild, String characterState)
        implements CharacterSheetData {
    public CharacterSheetData2024(String characterName, int level, boolean heroicInspiration) {
        this(characterName, level, heroicInspiration, "", "", "", "", "", "", "");
    }
    public CharacterSheetData2024(String characterName, int level, boolean heroicInspiration, String race, String characterClass, String background, String startingAbilities) {
        this(characterName, level, heroicInspiration, race, characterClass, background, startingAbilities, "", "", "");
    }
    public CharacterSheetData2024(String characterName, int level, boolean heroicInspiration, String race, String characterClass, String background, String startingAbilities, String derivedStatistics) { this(characterName, level, heroicInspiration, race, characterClass, background, startingAbilities, derivedStatistics, "", ""); }
    public CharacterSheetData2024 {
        characterName = CharacterSheetDataValidation.name(characterName);
        race = value(race); characterClass = value(characterClass); background = value(background); startingAbilities = value(startingAbilities); derivedStatistics = value(derivedStatistics); characterBuild = value(characterBuild); characterState = value(characterState);
        CharacterSheetDataValidation.level(level);
    }

    private static String value(String value) { return value == null ? "" : value.trim(); }

    @Override public SheetEdition edition() { return SheetEdition.DND_5E_2024; }
}
