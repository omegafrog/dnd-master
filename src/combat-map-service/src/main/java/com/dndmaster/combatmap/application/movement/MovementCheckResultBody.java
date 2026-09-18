package com.dndmaster.combatmap.application.movement;

import java.util.Objects;
import java.util.UUID;

public record MovementCheckResultBody(UUID operationId, UUID checkId, Boolean success) {
    public MovementCheckResultBody {
        Objects.requireNonNull(operationId, "operation id is required");
        Objects.requireNonNull(checkId, "check id is required");
        Objects.requireNonNull(success, "check success is required");
    }
}
