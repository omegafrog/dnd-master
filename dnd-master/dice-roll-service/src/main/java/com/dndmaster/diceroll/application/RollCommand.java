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
        long expectedVersion) {
    public RollCommand {
        Objects.requireNonNull(adventureId, "adventure id must not be null");
        Objects.requireNonNull(ruleSetId, "rule set id must not be null");
        Objects.requireNonNull(scope, "roll scope must not be null");
        Objects.requireNonNull(expression, "dice expression must not be null");
        Objects.requireNonNull(sessionId, "session id must not be null");
        Objects.requireNonNull(turnId, "turn id must not be null");
        Objects.requireNonNull(commandId, "command id must not be null");
        if (expectedVersion < 0) throw new IllegalArgumentException("expected version must not be negative");
    }
}
