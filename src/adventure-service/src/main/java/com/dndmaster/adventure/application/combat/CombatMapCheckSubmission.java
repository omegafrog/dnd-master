package com.dndmaster.adventure.application.combat;

import java.util.Objects;
import java.util.UUID;

public record CombatMapCheckSubmission(UUID operationId, UUID checkId, boolean success, UUID ownerPlayerId,
        CombatMapCheckActor actor, Integer rollTotal) {
    public CombatMapCheckSubmission(UUID operationId, UUID checkId, boolean success, UUID ownerPlayerId,
            CombatMapCheckActor actor) {
        this(operationId, checkId, success, ownerPlayerId, actor, null);
    }
    public CombatMapCheckSubmission {
        Objects.requireNonNull(operationId, "operation id is required");
        Objects.requireNonNull(checkId, "check id is required");
        Objects.requireNonNull(ownerPlayerId, "owner player id is required");
        Objects.requireNonNull(actor, "check actor is required");
        if (actor != CombatMapCheckActor.PLAYER) throw new IllegalArgumentException("unsupported check actor");
        if (rollTotal != null && (rollTotal < 1 || rollTotal > 20)) throw new IllegalArgumentException("roll total must be between 1 and 20");
    }
}
