package com.dndmaster.character.domain;

public sealed interface CharacterSheetData permits CharacterSheetData2014, CharacterSheetData2024 {
    String characterName();
    String race();
    String characterClass();
    String background();
    String startingAbilities();
    int level();
    SheetEdition edition();
}
