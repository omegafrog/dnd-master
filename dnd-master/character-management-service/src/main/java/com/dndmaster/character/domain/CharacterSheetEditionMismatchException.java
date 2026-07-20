package com.dndmaster.character.domain;

public final class CharacterSheetEditionMismatchException extends RuntimeException {
    public CharacterSheetEditionMismatchException() { super("character sheet edition does not match adventure edition"); }
}
