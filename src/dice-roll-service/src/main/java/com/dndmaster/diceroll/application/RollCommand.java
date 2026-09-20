package com.dndmaster.diceroll.application;

import com.dndmaster.diceroll.domain.AdventureId;
import com.dndmaster.diceroll.domain.DiceExpression;
import com.dndmaster.diceroll.domain.RollScope;
import com.dndmaster.diceroll.domain.RuleSetId;
import java.util.Objects;
import java.util.UUID;

public record RollCommand(
        AdventureId adventureId,
        RuleSetId ruleSetId,
        RollScope scope,
        DiceExpression expression,
        UUID sessionId,
        UUID turnId,
        UUID commandId,
        long expectedVersion,
        String ruleReference,
        Integer difficulty) {
    public RollCommand(AdventureId adventureId, RuleSetId ruleSetId, RollScope scope, DiceExpression expression,
            UUID sessionId, UUID turnId, UUID commandId, long expectedVersion) {
        this(adventureId, ruleSetId, scope, expression, sessionId, turnId, commandId, expectedVersion, null, null);
    }

    public RollCommand {
        Objects.requireNonNull(adventureId, "adventure id must not be null");
        Objects.requireNonNull(ruleSetId, "rule set id must not be null");
        Objects.requireNonNull(scope, "roll scope must not be null");
        Objects.requireNonNull(expression, "dice expression must not be null");
        Objects.requireNonNull(sessionId, "session id must not be null");
        Objects.requireNonNull(turnId, "turn id must not be null");
        Objects.requireNonNull(commandId, "command id must not be null");
        if (expectedVersion < 0) throw new IllegalArgumentException("expected version must not be negative");
        if (ruleReference != null && ruleReference.isBlank()) {
            throw new IllegalArgumentException("rule reference must not be blank when provided");
        }
        if (difficulty != null && difficulty < 0) throw new IllegalArgumentException("difficulty must not be negative");
    }
}
