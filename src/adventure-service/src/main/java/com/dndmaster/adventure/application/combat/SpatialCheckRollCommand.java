package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.adventure.RuleSetId;
import java.util.Objects;
import java.util.UUID;

/** 서버가 플레이어 공간 판정을 위해 주사위 서비스에 보내는 명령. */
public record SpatialCheckRollCommand(UUID adventureId, UUID mapId, UUID sessionId, RuleSetId ruleSetId,
        UUID ownerPlayerId, UUID checkId, UUID operationId, UUID commandId,
        String ruleReference, String diceExpression, int modifier, Integer difficulty, long expectedVersion) {
    public SpatialCheckRollCommand(UUID adventureId, UUID mapId, UUID sessionId, RuleSetId ruleSetId,
            UUID ownerPlayerId, UUID checkId, UUID operationId, long expectedVersion) {
        this(adventureId, mapId, sessionId, ruleSetId, ownerPlayerId, checkId, operationId,
                checkId, "legacy.spatial-check", "1d20", 0, null, expectedVersion);
    }

    public SpatialCheckRollCommand(UUID adventureId, UUID mapId, UUID sessionId, RuleSetId ruleSetId,
            UUID ownerPlayerId, UUID checkId, UUID operationId, UUID commandId, long expectedVersion) {
        this(adventureId, mapId, sessionId, ruleSetId, ownerPlayerId, checkId, operationId,
                commandId, "legacy.spatial-check", "1d20", 0, null, expectedVersion);
    }

    public SpatialCheckRollCommand {
        Objects.requireNonNull(adventureId, "adventure id must not be null");
        Objects.requireNonNull(mapId, "map id must not be null");
        Objects.requireNonNull(sessionId, "session id must not be null");
        Objects.requireNonNull(ruleSetId, "rule set id must not be null");
        Objects.requireNonNull(ownerPlayerId, "owner player id must not be null");
        Objects.requireNonNull(checkId, "check id must not be null");
        Objects.requireNonNull(operationId, "operation id must not be null");
        Objects.requireNonNull(commandId, "command id must not be null");
        if (ruleReference == null || ruleReference.isBlank()) throw new IllegalArgumentException("rule reference must not be blank");
        if (diceExpression == null || diceExpression.isBlank()) throw new IllegalArgumentException("dice expression must not be blank");
        if (expectedVersion < 0) throw new IllegalArgumentException("expected map version must be non-negative");
    }

    public SpatialCheckRollCommand withRule(CombatMapCheckDetails details) {
        return new SpatialCheckRollCommand(adventureId, mapId, sessionId, ruleSetId, ownerPlayerId, checkId, operationId,
                commandId, details.ruleReference(), details.diceExpression(), details.modifier(), details.difficulty(), expectedVersion);
    }
}
