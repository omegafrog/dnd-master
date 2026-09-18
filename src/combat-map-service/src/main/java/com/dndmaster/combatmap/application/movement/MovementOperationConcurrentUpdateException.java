package com.dndmaster.combatmap.application.movement;

/** A concurrent resume changed the durable operation before this caller could save it. */
public final class MovementOperationConcurrentUpdateException extends IllegalStateException {
    public MovementOperationConcurrentUpdateException() { super("movement operation changed concurrently"); }
}
