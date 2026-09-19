package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.adventure.RuleSetId;
import java.util.Objects;
import java.util.UUID;

/** 내부 적 소유 인지 판정을 위한 명령이다. 플레이어 판정 경계와 분리한다. */
public record EnemyObservationRollCommand(UUID adventureId, UUID mapId, UUID sessionId, RuleSetId ruleSetId,
        UUID ownerPlayerId, UUID checkId, UUID operationId, UUID commandId, String ruleReference,
        String diceExpression, int modifier, Integer difficulty, long expectedVersion) {
    public EnemyObservationRollCommand {
        Objects.requireNonNull(adventureId); Objects.requireNonNull(mapId); Objects.requireNonNull(sessionId);
        Objects.requireNonNull(ruleSetId); Objects.requireNonNull(ownerPlayerId); Objects.requireNonNull(checkId);
        Objects.requireNonNull(operationId); Objects.requireNonNull(commandId);
        if (ruleReference == null || ruleReference.isBlank()) throw new IllegalArgumentException("rule reference must not be blank");
        if (diceExpression == null || diceExpression.isBlank()) throw new IllegalArgumentException("dice expression must not be blank");
        if (expectedVersion < 0) throw new IllegalArgumentException("expected map version must be non-negative");
    }
    public EnemyObservationRollCommand withRule(CombatMapCheckDetails details) {
        return new EnemyObservationRollCommand(adventureId, mapId, sessionId, ruleSetId, ownerPlayerId, checkId,
                operationId, commandId, details.ruleReference(), details.diceExpression(), details.modifier(), details.difficulty(), expectedVersion);
    }
}
