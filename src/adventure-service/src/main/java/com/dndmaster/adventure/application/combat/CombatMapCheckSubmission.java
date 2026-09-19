package com.dndmaster.adventure.application.combat;

import java.util.Objects;
import java.util.UUID;

public record CombatMapCheckSubmission(UUID operationId, UUID checkId, boolean success, UUID ownerPlayerId,
        CombatMapCheckActor actor) {
    public CombatMapCheckSubmission {
        Objects.requireNonNull(operationId, "operation id is required");
        Objects.requireNonNull(checkId, "check id is required");
        Objects.requireNonNull(ownerPlayerId, "owner player id is required");
        Objects.requireNonNull(actor, "check actor is required");
        if (actor != CombatMapCheckActor.PLAYER) throw new IllegalArgumentException("unsupported check actor");
    }
}
