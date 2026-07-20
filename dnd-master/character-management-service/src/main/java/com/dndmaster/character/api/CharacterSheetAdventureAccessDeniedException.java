package com.dndmaster.character.api;

public final class CharacterSheetAdventureAccessDeniedException extends RuntimeException {
    public CharacterSheetAdventureAccessDeniedException() {
        super("character sheet does not belong to requested adventure");
    }
}
