package com.dndmaster.character.application;

public final class CharacterSheetNotFoundException extends RuntimeException {
    public CharacterSheetNotFoundException() { super("character sheet was not found"); }
}
