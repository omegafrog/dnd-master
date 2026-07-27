package com.dndmaster.adventure.application.combat;

public interface AiCombatPort {
    void controlState(CombatActionCommand command);
    String adjudicate(CombatActionCommand command, int diceTotal);
}
