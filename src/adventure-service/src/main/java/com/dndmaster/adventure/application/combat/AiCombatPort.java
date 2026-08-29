package com.dndmaster.adventure.application.combat;

public interface AiCombatPort {
    void controlState(CombatActionCommand command);
    String adjudicate(CombatActionCommand command, int diceTotal);

    /** Structured effects are opt-in; the legacy prose result remains display-only. */
    default CombatOutcome adjudicateOutcome(CombatActionCommand command, int diceTotal) {
        return new CombatOutcome(adjudicate(command, diceTotal));
    }
}
