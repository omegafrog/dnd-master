package com.dndmaster.combatmap.application.movement;

import java.util.Objects;
import java.util.UUID;

/** Separate cancellation command identity bound to one movement operation. */
public record MovementCancelRequest(UUID operationId, UUID commandId) {
    public MovementCancelRequest {
        Objects.requireNonNull(operationId, "operation id must not be null");
        Objects.requireNonNull(commandId, "cancel command id must not be null");
    }
}
