package com.dndmaster.diceroll.domain;

public record DiceExpression(int count, int sides, int modifier) {
    public DiceExpression {
        if (count < 1 || count > 100) throw new IllegalArgumentException("dice count must be between 1 and 100");
        if (sides < 2 || sides > 1000) throw new IllegalArgumentException("dice sides must be between 2 and 1000");
        if (modifier < -10_000 || modifier > 10_000) throw new IllegalArgumentException("dice modifier is out of range");
    }
}
