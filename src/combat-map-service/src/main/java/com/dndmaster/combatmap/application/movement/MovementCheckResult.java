package com.dndmaster.combatmap.application.movement;

import java.util.Objects;
import java.util.UUID;
import com.dndmaster.combatmap.domain.PlayerId;

/** Adventure Runtime이 검증한 판정 결과. 결과 자체만 Combat Map에 전달한다. */
public record MovementCheckResult(UUID operationId, UUID checkId, boolean success, MovementCheckOwner owner) {
    public MovementCheckResult {
        Objects.requireNonNull(operationId, "operation id must not be null");
        Objects.requireNonNull(checkId, "check id must not be null");
        Objects.requireNonNull(owner, "check owner must not be null");
    }

    public MovementCheckResult(UUID operationId, UUID checkId, boolean success, PlayerId ownerPlayerId) {
        this(operationId, checkId, success, MovementCheckOwner.player(ownerPlayerId));
    }
}
