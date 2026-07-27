package com.dndmaster.adventure.infrastructure.persistence;

public final class OptimisticAdventureLockException extends RuntimeException {
    public OptimisticAdventureLockException() { super("adventure was concurrently modified"); }
}
