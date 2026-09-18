package com.dndmaster.combatmap.application.movement;

import java.util.Objects;
import java.util.UUID;

public record MovementCheckResultBody(UUID operationId, UUID checkId, Boolean success, UUID ownerPlayerId,
        MovementCheckActor actor) {
    public MovementCheckResultBody {
        Objects.requireNonNull(operationId, "operation id is required");
        Objects.requireNonNull(checkId, "check id is required");
        Objects.requireNonNull(success, "check success is required");
        Objects.requireNonNull(ownerPlayerId, "check owner player id is required");
        Objects.requireNonNull(actor, "check actor is required");
    }
}
