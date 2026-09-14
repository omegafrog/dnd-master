package com.dndmaster.combatmap.application.view;

/** Map activation must wait for a valid agent proposal or an explicit user placement. */
public final class MapPlacementRequiredException extends IllegalStateException {
    public MapPlacementRequiredException() {
        super("MAP_SPAWN_REVIEW_REQUIRED");
    }
}
