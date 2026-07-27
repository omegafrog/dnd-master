package com.dndmaster.diceroll.infrastructure.persistence;

public final class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException() { super("delivery key was reused with different payload"); }
}
