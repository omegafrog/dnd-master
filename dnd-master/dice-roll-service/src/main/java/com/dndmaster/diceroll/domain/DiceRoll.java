package com.dndmaster.diceroll.domain;

import java.util.Objects;
import java.util.Optional;

public final class DiceRoll {
    private final RollId id;
    private final AdventureId adventureId;
    private final RuleSetId ruleSetId;
    private final RollScope scope;
    private final DiceExpression expression;
    private DiceResult result;

    public DiceRoll(
            RollId id, AdventureId adventureId, RuleSetId ruleSetId,
            RollScope scope, DiceExpression expression) {
        this.id = Objects.requireNonNull(id, "roll id must not be null");
        this.adventureId = Objects.requireNonNull(adventureId, "adventure id must not be null");
        this.ruleSetId = Objects.requireNonNull(ruleSetId, "rule set id must not be null");
        this.scope = Objects.requireNonNull(scope, "roll scope must not be null");
        this.expression = Objects.requireNonNull(expression, "dice expression must not be null");
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
    public Optional<DiceResult> result() { return Optional.ofNullable(result); }
}
