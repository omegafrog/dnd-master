package com.dndmaster.combatmap.application.movement;

/** A staged movement must be bound to a server-generated public preview. */
public final class MovementPreviewRequiredException extends IllegalArgumentException {
    public MovementPreviewRequiredException() {
        super("movement preview fingerprint is required");
    }
}
