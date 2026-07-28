package com.dndmaster.adventure.domain.scenario;

public final class CharacterCreationBlueprintRevisionConflictException extends IllegalStateException {
    public CharacterCreationBlueprintRevisionConflictException(long expected, long actual) {
        super("character blueprint revision conflict: expected " + expected + ", actual " + actual);
    }
}
