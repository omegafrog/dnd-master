package com.dndmaster.adventure.application.combat;

@FunctionalInterface
public interface CombatAiTurnEndExecutor {
    CombatActionResponse execute(CombatActionCommand command);
}
