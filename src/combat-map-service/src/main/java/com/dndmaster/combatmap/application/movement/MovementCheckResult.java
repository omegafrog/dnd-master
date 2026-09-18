package com.dndmaster.combatmap.application.movement;

import java.util.Objects;
import java.util.UUID;

/** Adventure Runtime이 검증한 판정 결과. 결과 자체만 Combat Map에 전달한다. */
public record MovementCheckResult(UUID operationId, UUID checkId, boolean success) {
    public MovementCheckResult {
        Objects.requireNonNull(operationId, "operation id must not be null");
        Objects.requireNonNull(checkId, "check id must not be null");
    }
}
