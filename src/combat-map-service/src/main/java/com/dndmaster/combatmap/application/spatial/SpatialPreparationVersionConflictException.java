package com.dndmaster.combatmap.application.spatial;

public final class SpatialPreparationVersionConflictException extends RuntimeException {
    public SpatialPreparationVersionConflictException() {
        super("spatial preparation map version is stale");
    }
}
