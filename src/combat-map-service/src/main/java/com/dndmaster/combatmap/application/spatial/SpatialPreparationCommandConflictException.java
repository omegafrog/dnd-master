package com.dndmaster.combatmap.application.spatial;

public final class SpatialPreparationCommandConflictException extends RuntimeException {
    public SpatialPreparationCommandConflictException() {
        super("spatial preparation command id was reused with a different payload");
    }
}
