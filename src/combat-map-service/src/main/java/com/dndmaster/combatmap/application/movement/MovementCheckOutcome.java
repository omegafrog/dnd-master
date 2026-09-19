package com.dndmaster.combatmap.application.movement;

import java.util.Objects;
import java.util.UUID;

/** 재시작 뒤 같은 셀 판정을 다시 만들지 않기 위한 내부 결과 기록. */
public record MovementCheckOutcome(UUID featureId, UUID checkId, UUID commandId,
        MovementCheckOwner owner, boolean success) {
    public MovementCheckOutcome(UUID featureId, boolean success) {
        this(featureId, null, null, null, success);
    }

    public MovementCheckOutcome {
        Objects.requireNonNull(featureId, "feature id must not be null");
    }

    public boolean matches(MovementCheckResult result) {
        return commandId != null && commandId.equals(result.commandId())
                && checkId != null && checkId.equals(result.checkId())
                && owner != null && owner.equals(result.owner())
                && success == result.success();
    }
}
