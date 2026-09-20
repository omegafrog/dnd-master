package com.dndmaster.combatmap.domain;

import java.util.Objects;

/** 룰북의 적 지각 판정에 필요한 내부 규격이다. 플레이어 투영으로 복사하지 않는다. */
public record HostileObservationRule(String ruleReference, String diceExpression,
        int modifier, Integer difficulty, String mode) {
    public HostileObservationRule {
        if (ruleReference == null || ruleReference.isBlank()) {
            throw new IllegalArgumentException("hostile observation rule reference must not be blank");
        }
        if (diceExpression == null || diceExpression.isBlank()) {
            throw new IllegalArgumentException("hostile observation dice expression must not be blank");
        }
        if (modifier < -10_000 || modifier > 10_000) {
            throw new IllegalArgumentException("hostile observation modifier is out of range");
        }
        if (difficulty != null && difficulty < 0) {
            throw new IllegalArgumentException("hostile observation difficulty must not be negative");
        }
        mode = mode == null || mode.isBlank() ? "SYSTEM" : mode.trim();
        Objects.requireNonNull(mode, "hostile observation mode must not be null");
    }

    public HostileObservationRule(String ruleReference, Integer difficulty, String mode) {
        this(ruleReference, "1d20", 0, difficulty, mode);
    }
}
