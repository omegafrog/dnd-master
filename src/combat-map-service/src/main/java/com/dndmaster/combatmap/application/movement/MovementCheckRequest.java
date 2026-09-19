package com.dndmaster.combatmap.application.movement;

import com.dndmaster.combatmap.domain.SpatialFeatureType;
import com.dndmaster.combatmap.domain.SpatialTrigger;
import java.util.Objects;
import java.util.UUID;

/** 내부 판정 요청. feature id와 난이도는 플레이어 투영으로 복사하지 않는다. */
public record MovementCheckRequest(UUID checkId, UUID operationId, UUID featureId,
        SpatialFeatureType featureType, SpatialTrigger trigger, String ruleReference,
        String diceExpression, int modifier, Integer difficulty, String mode, MovementCheckOwner owner) {
    public MovementCheckRequest(UUID checkId, UUID operationId, UUID featureId,
            SpatialFeatureType featureType, SpatialTrigger trigger, String ruleReference,
            Integer difficulty, String mode, MovementCheckOwner owner) {
        this(checkId, operationId, featureId, featureType, trigger, ruleReference,
                "1d20", 0, difficulty, mode, owner);
    }

    public MovementCheckRequest {
        Objects.requireNonNull(checkId, "check id must not be null");
        Objects.requireNonNull(operationId, "operation id must not be null");
        Objects.requireNonNull(featureId, "feature id must not be null");
        Objects.requireNonNull(featureType, "feature type must not be null");
        Objects.requireNonNull(trigger, "trigger must not be null");
        if (ruleReference == null || ruleReference.isBlank()) throw new IllegalArgumentException("rule reference must not be blank");
        if (diceExpression == null || diceExpression.isBlank()) throw new IllegalArgumentException("dice expression must not be blank");
        if (modifier < -10_000 || modifier > 10_000) throw new IllegalArgumentException("modifier is out of range");
        if (difficulty != null && difficulty < 0) throw new IllegalArgumentException("difficulty must not be negative");
        mode = mode == null || mode.isBlank() ? "SYSTEM" : mode.trim();
        owner = Objects.requireNonNull(owner, "check owner must not be null");
    }

    public PendingMovementCheck playerView() {
        String label = "지각 판정";
        return new PendingMovementCheck(checkId, operationId, label, diceExpression, owner);
    }
}
