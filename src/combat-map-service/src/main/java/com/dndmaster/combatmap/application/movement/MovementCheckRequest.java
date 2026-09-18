package com.dndmaster.combatmap.application.movement;

import com.dndmaster.combatmap.domain.SpatialFeatureType;
import com.dndmaster.combatmap.domain.SpatialTrigger;
import java.util.Objects;
import java.util.UUID;

/** 내부 판정 요청. feature id와 난이도는 플레이어 투영으로 복사하지 않는다. */
public record MovementCheckRequest(UUID checkId, UUID operationId, UUID featureId,
        SpatialFeatureType featureType, SpatialTrigger trigger, String ruleReference,
        Integer difficulty, String mode, String ownership) {
    public MovementCheckRequest {
        Objects.requireNonNull(checkId, "check id must not be null");
        Objects.requireNonNull(operationId, "operation id must not be null");
        Objects.requireNonNull(featureId, "feature id must not be null");
        Objects.requireNonNull(featureType, "feature type must not be null");
        Objects.requireNonNull(trigger, "trigger must not be null");
        if (ruleReference == null || ruleReference.isBlank()) throw new IllegalArgumentException("rule reference must not be blank");
        if (difficulty != null && difficulty < 0) throw new IllegalArgumentException("difficulty must not be negative");
        mode = mode == null || mode.isBlank() ? "SYSTEM" : mode.trim();
        ownership = ownership == null || ownership.isBlank() ? "SYSTEM" : ownership.trim();
    }

    public PendingMovementCheck playerView() {
        String label = "지각 판정";
        return new PendingMovementCheck(checkId, operationId, label, "d20", ownership);
    }
}
