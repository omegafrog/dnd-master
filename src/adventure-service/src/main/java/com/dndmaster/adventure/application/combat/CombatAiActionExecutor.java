package com.dndmaster.adventure.application.combat;

@FunctionalInterface
public interface CombatAiActionExecutor {
    CombatActionResponse execute(CombatActionCommand command, AiTurnPlan plan);
}
