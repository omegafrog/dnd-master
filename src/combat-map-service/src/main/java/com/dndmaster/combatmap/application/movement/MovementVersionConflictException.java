package com.dndmaster.combatmap.application.movement;

public final class MovementVersionConflictException extends IllegalStateException {
    public MovementVersionConflictException() { super("combat map version changed before movement reservation"); }
}
