package com.dndmaster.adventure.application.combat;

public interface CombatMapPort {
    /** Legacy adapter entry point retained for runtime-turn compatibility. */
    void validateAndMove(CombatActionCommand command);

    /** Dedicated movement boundary carrying the map version and command identity. */
    default CombatMapMoveResult move(CombatMapMoveCommand command) {
        validateAndMove(command.action());
        return new CombatMapMoveResult(command.action().expectedVersion() + 1);
    }

    /** Validates a player destination without changing Combat Map state. */
    default CombatMapPreviewResult preview(CombatMapPreviewCommand command) {
        throw new UnsupportedOperationException("combat map movement preview is unavailable");
    }

    /** Recovery boundary for a durable map-owned movement reservation. */
    default CombatMapMoveResult movementOperation(java.util.UUID mapId, java.util.UUID operationId) { throw new UnsupportedOperationException("movement operation query is unavailable"); }
    /** Loads the most recent durable operation when the client has no local operation identity. */
    default CombatMapMoveResult latestMovementOperation(java.util.UUID mapId) { throw new UnsupportedOperationException("latest movement operation query is unavailable"); }
    default CombatMapMoveResult resumeMovementOperation(java.util.UUID mapId, java.util.UUID operationId) { throw new UnsupportedOperationException("movement operation resume is unavailable"); }
    default CombatMapMoveResult resumeMovementOperation(java.util.UUID mapId, java.util.UUID operationId, CombatMapCheckSubmission submission) {
        return resumeMovementOperation(mapId, operationId);
    }
    default CombatMapMoveResult cancelMovementOperation(java.util.UUID mapId, java.util.UUID operationId,
            java.util.UUID cancelCommandId) { throw new UnsupportedOperationException("movement operation cancellation is unavailable"); }

    /** Terminal map-owner boundary; map state remains owned by Combat Map. */
    default void commitFinalState(CombatFinalizationCommand command) {}

    default CombatMapSpatialResult observe(CombatMapSpatialActionCommand command) {
        throw new UnsupportedOperationException("spatial observe is unavailable");
    }
    default CombatMapSpatialResult interact(CombatMapSpatialActionCommand command) {
        throw new UnsupportedOperationException("spatial interact is unavailable");
    }
    default CombatMapSpatialResult combatTurnStart(CombatMapSpatialTurnCommand command) {
        throw new UnsupportedOperationException("spatial combat turn start is unavailable");
    }
    default CombatMapSpatialResult advanceDurations(CombatMapSpatialTurnCommand command) {
        throw new UnsupportedOperationException("spatial duration advance is unavailable");
    }

    /** Player-owned spatial checks are rolled by the typed dice gateway before map resume. */
    default int rollSpatialCheck(SpatialCheckRollCommand command) {
        throw new UnsupportedOperationException("spatial check dice roll is unavailable");
    }
    default int rollEnemyObservation(EnemyObservationRollCommand command) {
        throw new UnsupportedOperationException("enemy observation dice roll is unavailable");
    }
}
