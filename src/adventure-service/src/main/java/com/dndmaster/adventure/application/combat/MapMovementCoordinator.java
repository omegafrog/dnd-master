package com.dndmaster.adventure.application.combat;

import java.util.Objects;

/** Shared Adventure-side boundary for confirmed movement from runtime and map actions. */
public final class MapMovementCoordinator {
    private final CombatMapPort combatMap;

    public MapMovementCoordinator(CombatMapPort combatMap) {
        this.combatMap = Objects.requireNonNull(combatMap, "combat map port must not be null");
    }

    public CombatMapMoveResult resolve(CombatMapMoveCommand command) {
        return combatMap.move(Objects.requireNonNull(command, "map movement command must not be null"));
    }
}
