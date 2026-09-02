package com.dndmaster.adventure.application.runtime;

import java.util.UUID;

/** Player-safe projection of a pending runtime roll. */
public record PlayerRollRequest(UUID pendingTurnId, String label, String diceExpression,
                                String prompt, long expectedVersion) {
    public PlayerRollRequest {
        if (pendingTurnId == null) throw new IllegalArgumentException("pending turn id is required");
        label = label == null ? "판정" : label.trim();
        diceExpression = diceExpression == null ? "d20" : diceExpression.trim();
        prompt = prompt == null ? "d20을 굴려 결과를 제출하세요." : prompt.trim();
        if (expectedVersion < 0) throw new IllegalArgumentException("expected version must be non-negative");
    }
}
