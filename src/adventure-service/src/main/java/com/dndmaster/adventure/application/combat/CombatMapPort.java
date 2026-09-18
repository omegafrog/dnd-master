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
    default CombatMapMoveResult cancelMovementOperation(java.util.UUID mapId, java.util.UUID operationId) { throw new UnsupportedOperationException("movement operation cancellation is unavailable"); }

    /** Terminal map-owner boundary; map state remains owned by Combat Map. */
    default void commitFinalState(CombatFinalizationCommand command) {}
}
