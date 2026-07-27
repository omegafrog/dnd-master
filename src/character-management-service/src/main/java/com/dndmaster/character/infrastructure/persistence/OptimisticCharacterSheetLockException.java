package com.dndmaster.character.infrastructure.persistence;

public final class OptimisticCharacterSheetLockException extends RuntimeException {
    public OptimisticCharacterSheetLockException() {
        super("character sheet was concurrently modified");
    }
}
