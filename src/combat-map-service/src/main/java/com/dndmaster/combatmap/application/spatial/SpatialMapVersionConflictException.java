package com.dndmaster.combatmap.application.spatial;

public final class SpatialMapVersionConflictException extends RuntimeException {
    public static final String ERROR_CODE = "SPATIAL_MAP_VERSION_CONFLICT";

    public SpatialMapVersionConflictException() {
        super("combat map version changed while applying a spatial action");
    }
}
