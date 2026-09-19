package com.dndmaster.combatmap.application.movement;

import java.util.Objects;
import java.util.UUID;

public record MovementCheckResultBody(UUID operationId, UUID checkId, Boolean success, UUID ownerPlayerId,
        MovementCheckActor actor, Integer rollTotal) {
    public MovementCheckResultBody(UUID operationId, UUID checkId, Boolean success, UUID ownerPlayerId,
            MovementCheckActor actor) {
        this(operationId, checkId, success, ownerPlayerId, actor, null);
    }
    public MovementCheckResultBody {
        Objects.requireNonNull(operationId, "operation id is required");
        Objects.requireNonNull(checkId, "check id is required");
        if (success == null && rollTotal == null) throw new IllegalArgumentException("check result is required");
        Objects.requireNonNull(ownerPlayerId, "check owner player id is required");
        Objects.requireNonNull(actor, "check actor is required");
        if (rollTotal != null && (rollTotal < 1 || rollTotal > 20)) throw new IllegalArgumentException("roll total must be between 1 and 20");
    }
}
