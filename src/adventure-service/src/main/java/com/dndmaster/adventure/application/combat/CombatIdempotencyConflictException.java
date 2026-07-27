package com.dndmaster.adventure.application.combat;

public final class CombatIdempotencyConflictException extends RuntimeException {
    public CombatIdempotencyConflictException() { super("operation id was reused for a different combat action"); }
}
