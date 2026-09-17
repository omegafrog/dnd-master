package com.dndmaster.combatmap.domain;

import java.util.Objects;

/** 룰 참조와 탐지 방식만 저장한다. 플레이어 응답으로 직접 노출하지 않는다. */
public record DetectionSpec(String ruleReference, Integer difficulty, String mode) {
    public DetectionSpec {
        ruleReference = required(ruleReference, "rule reference");
        mode = required(mode, "detection mode");
        if (difficulty != null && difficulty < 0) throw new IllegalArgumentException("detection difficulty must not be negative");
    }

    public static DetectionSpec passive(String ruleReference, int difficulty) {
        return new DetectionSpec(ruleReference, difficulty, "PASSIVE");
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
