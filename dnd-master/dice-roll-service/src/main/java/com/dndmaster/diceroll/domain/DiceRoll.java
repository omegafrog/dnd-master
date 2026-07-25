package com.dndmaster.diceroll.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class DiceRoll {
    private final RollId id;
    private final AdventureId adventureId;
    private final RuleSetId ruleSetId;
    private final RollScope scope;
    private final DiceExpression expression;
    private final UUID sessionId;
    private final UUID turnId;
    private final UUID commandId;
    private final long expectedVersion;
    private DiceResult result;

    public DiceRoll(
            RollId id, AdventureId adventureId, RuleSetId ruleSetId,
            RollScope scope, DiceExpression expression,
            UUID sessionId, UUID turnId, UUID commandId, long expectedVersion) {
        this.id = Objects.requireNonNull(id, "roll id must not be null");
        this.adventureId = Objects.requireNonNull(adventureId, "adventure id must not be null");
        this.ruleSetId = Objects.requireNonNull(ruleSetId, "rule set id must not be null");
        this.scope = Objects.requireNonNull(scope, "roll scope must not be null");
        this.expression = Objects.requireNonNull(expression, "dice expression must not be null");
        this.sessionId = Objects.requireNonNull(sessionId, "session id must not be null");
        this.turnId = Objects.requireNonNull(turnId, "turn id must not be null");
        this.commandId = Objects.requireNonNull(commandId, "command id must not be null");
        if (expectedVersion < 0) throw new IllegalArgumentException("expected version must not be negative");
        this.expectedVersion = expectedVersion;
    }

    public void authorizePlayerExecution() {
        if (scope != RollScope.PLAYER_ACTION) {
            throw new RollPermissionDeniedException("player can execute only PLAYER_ACTION rolls");
        }
    }

    public void authorizeAiExecution() {
        if (scope == RollScope.PLAYER_ACTION) {
            throw new RollPermissionDeniedException("AI cannot execute PLAYER_ACTION rolls");
        }
    }

    public void recordBuiltInResult(DiceResult newResult) {
        if (result != null) throw new IllegalStateException("dice result has already been recorded");
        Objects.requireNonNull(newResult, "dice result must not be null").validateAgainst(expression);
        result = newResult;
    }

    public RollId id() { return id; }
    public AdventureId adventureId() { return adventureId; }
    public RuleSetId ruleSetId() { return ruleSetId; }
    public RollScope scope() { return scope; }
    public DiceExpression expression() { return expression; }
    public UUID sessionId() { return sessionId; }
    public UUID turnId() { return turnId; }
    public UUID commandId() { return commandId; }
    public long expectedVersion() { return expectedVersion; }
    public Optional<DiceResult> result() { return Optional.ofNullable(result); }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DiceRoll roll)) return false;
        return expectedVersion == roll.expectedVersion
                && id.equals(roll.id)
                && adventureId.equals(roll.adventureId)
                && ruleSetId.equals(roll.ruleSetId)
                && scope == roll.scope
                && expression.equals(roll.expression)
                && sessionId.equals(roll.sessionId)
                && turnId.equals(roll.turnId)
                && commandId.equals(roll.commandId)
                && Objects.equals(result, roll.result);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, adventureId, ruleSetId, scope, expression, sessionId, turnId, commandId, expectedVersion, result);
    }
}
