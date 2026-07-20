package com.dndmaster.character.domain;

public sealed interface CharacterSheetData permits CharacterSheetData2014, CharacterSheetData2024 {
    String characterName();
    int level();
    SheetEdition edition();
}
