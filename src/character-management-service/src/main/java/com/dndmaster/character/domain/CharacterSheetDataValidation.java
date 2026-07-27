package com.dndmaster.character.domain;

final class CharacterSheetDataValidation {
    private CharacterSheetDataValidation() {}

    static String name(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("character name must not be blank");
        return value.trim();
    }

    static void level(int value) {
        if (value < 1 || value > 20) throw new IllegalArgumentException("character level must be between 1 and 20");
    }
}
