package com.dndmaster.adventure.application.combat;

import java.util.UUID;

/** Combat Map이 대기 중인 판정에서 공개해도 되는 최소 정보. */
public record CombatMapPendingCheck(UUID checkId, UUID operationId, String label,
        String diceExpression, UUID ownerPlayerId, CombatMapCheckActor actor) {
}
