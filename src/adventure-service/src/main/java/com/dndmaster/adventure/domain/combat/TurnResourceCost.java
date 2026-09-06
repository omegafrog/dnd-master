package com.dndmaster.adventure.domain.combat;

/** The resources reserved by one combat command. */
public record TurnResourceCost(int movement, boolean action, boolean bonusAction, boolean reaction) {
    public TurnResourceCost {
        if (movement < 0) throw new IllegalArgumentException("movement cost must be non-negative");
    }

    public static TurnResourceCost actionOnly() {
        return new TurnResourceCost(0, true, false, false);
    }

    public static TurnResourceCost movementOnly(int movement) {
        return new TurnResourceCost(movement, false, false, false);
    }

    public static TurnResourceCost reactionOnly() {
        return new TurnResourceCost(0, false, false, true);
    }
}
