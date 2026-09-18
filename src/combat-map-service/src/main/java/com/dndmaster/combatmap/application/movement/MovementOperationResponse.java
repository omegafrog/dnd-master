package com.dndmaster.combatmap.application.movement;

import java.util.UUID;

public record MovementOperationResponse(UUID operationId, MovementOperationStatus status,
        MovementResolutionResult result, PendingMovementCheck pendingCheck) {
    public MovementOperationResponse(UUID operationId, MovementOperationStatus status, MovementResolutionResult result) {
        this(operationId, status, result, null);
    }
    public MovementResolutionOutcomeStatus outcomeStatus() {
        if (result != null) return result.status();
        return switch (status) {
            case PREPARING, CHECK_PENDING, READY_TO_COMMIT -> MovementResolutionOutcomeStatus.CHECK_REQUIRED;
            case RETRY_WAIT -> MovementResolutionOutcomeStatus.RETRY_REQUIRED;
            case COMMITTED -> MovementResolutionOutcomeStatus.COMMITTED;
            case CANCELLED -> MovementResolutionOutcomeStatus.CANCELLED;
        };
    }
}
