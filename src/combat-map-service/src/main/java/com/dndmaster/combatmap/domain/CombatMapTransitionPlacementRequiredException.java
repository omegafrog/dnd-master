package com.dndmaster.combatmap.domain;

public final class CombatMapTransitionPlacementRequiredException extends IllegalStateException {
    public CombatMapTransitionPlacementRequiredException() { super("COMBAT_TOKEN_PLACEMENT_REQUIRED"); }
}
