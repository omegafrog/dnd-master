package com.dndmaster.combatmap.application.movement;

import java.util.Objects;
import java.util.UUID;

/** 플레이어에게 안전하게 공개할 수 있는 대기 판정 투영. */
public record PendingMovementCheck(UUID checkId, UUID operationId, String label,
        String diceExpression, String ownership) {
    public PendingMovementCheck {
        Objects.requireNonNull(checkId, "check id must not be null");
        Objects.requireNonNull(operationId, "operation id must not be null");
        label = label == null || label.isBlank() ? "판정" : label.trim();
        diceExpression = diceExpression == null || diceExpression.isBlank() ? "d20" : diceExpression.trim();
        ownership = ownership == null || ownership.isBlank() ? "SYSTEM" : ownership.trim();
    }
}
