package com.dndmaster.combatmap.application.movement;

import java.util.UUID;

public record MovementOperationResponse(UUID operationId, MovementOperationStatus status,
        MovementResolutionResult result) {
    public MovementResolutionOutcomeStatus outcomeStatus() {
        if (result != null) return result.status();
        return switch (status) {
            case PREPARING, READY_TO_COMMIT -> MovementResolutionOutcomeStatus.CHECK_REQUIRED;
            case RETRY_WAIT -> MovementResolutionOutcomeStatus.RETRY_REQUIRED;
            case COMMITTED -> MovementResolutionOutcomeStatus.COMMITTED;
            case CANCELLED -> MovementResolutionOutcomeStatus.CANCELLED;
        };
    }
}
