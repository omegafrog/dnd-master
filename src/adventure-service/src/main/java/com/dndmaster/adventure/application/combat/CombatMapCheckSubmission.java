package com.dndmaster.adventure.application.combat;

import java.util.Objects;
import java.util.UUID;

public record CombatMapCheckSubmission(UUID operationId, UUID checkId, boolean success) {
    public CombatMapCheckSubmission {
        Objects.requireNonNull(operationId, "operation id is required");
        Objects.requireNonNull(checkId, "check id is required");
    }
}
