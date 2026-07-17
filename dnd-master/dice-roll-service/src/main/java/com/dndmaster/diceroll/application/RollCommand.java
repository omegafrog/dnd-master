package com.dndmaster.diceroll.application;

import com.dndmaster.diceroll.domain.AdventureId;
import com.dndmaster.diceroll.domain.DiceExpression;
import com.dndmaster.diceroll.domain.RollScope;
import com.dndmaster.diceroll.domain.RuleSetId;
import java.util.Objects;

public record RollCommand(
        AdventureId adventureId, RuleSetId ruleSetId, RollScope scope, DiceExpression expression) {
    public RollCommand {
        Objects.requireNonNull(adventureId, "adventure id must not be null");
        Objects.requireNonNull(ruleSetId, "rule set id must not be null");
        Objects.requireNonNull(scope, "roll scope must not be null");
        Objects.requireNonNull(expression, "dice expression must not be null");
    }
}
