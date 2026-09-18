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
    public CombatMapMoveResult query(java.util.UUID mapId, java.util.UUID operationId) { return combatMap.movementOperation(mapId, operationId); }
    public CombatMapMoveResult latest(java.util.UUID mapId) { return combatMap.latestMovementOperation(mapId); }
    public CombatMapMoveResult resume(java.util.UUID mapId, java.util.UUID operationId) { return combatMap.resumeMovementOperation(mapId, operationId); }
    public CombatMapMoveResult resume(java.util.UUID mapId, java.util.UUID operationId, CombatMapCheckSubmission submission) {
        return combatMap.resumeMovementOperation(mapId, operationId, submission);
    }
    public CombatMapMoveResult cancel(java.util.UUID mapId, java.util.UUID operationId) { return combatMap.cancelMovementOperation(mapId, operationId); }
    public CombatMapSpatialResult observe(CombatMapSpatialActionCommand command) { return combatMap.observe(command); }
    public CombatMapSpatialResult interact(CombatMapSpatialActionCommand command) { return combatMap.interact(command); }
    public CombatMapSpatialResult combatTurnStart(CombatMapSpatialTurnCommand command) { return combatMap.combatTurnStart(command); }
    public CombatMapSpatialResult advanceDurations(CombatMapSpatialTurnCommand command) { return combatMap.advanceDurations(command); }
}
