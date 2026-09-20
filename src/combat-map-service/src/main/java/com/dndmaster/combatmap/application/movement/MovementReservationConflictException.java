package com.dndmaster.combatmap.application.movement;

public final class MovementReservationConflictException extends IllegalStateException {
    public MovementReservationConflictException() { super("another movement reservation is active for this map"); }
}
