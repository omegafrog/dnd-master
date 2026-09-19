package com.dndmaster.adventure.application.combat;

import java.util.UUID;

/** 내부 판정 어댑터가 규칙 경계에 전달하는 공간 판정 정보. */
public record CombatMapCheckDetails(UUID checkId, UUID operationId, String ruleReference,
        String diceExpression, int modifier, Integer difficulty, UUID ownerPlayerId, CombatMapCheckActor actor) {
    public CombatMapCheckDetails(UUID checkId, UUID operationId, String ruleReference,
            Integer difficulty, UUID ownerPlayerId, CombatMapCheckActor actor) {
        this(checkId, operationId, ruleReference, "1d20", 0, difficulty, ownerPlayerId, actor);
    }
}
