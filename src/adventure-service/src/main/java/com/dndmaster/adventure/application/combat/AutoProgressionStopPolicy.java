package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.combat.CombatEncounter;
import com.dndmaster.adventure.domain.combat.CombatParticipant;

/** Pure guard for the boundaries at which AI progression must yield. */
public final class AutoProgressionStopPolicy {
    public enum Reason { CONTINUE, HUMAN_TURN, REACTION_PENDING, ENDED, MAX_STEPS, NOT_AI_TURN }

    private AutoProgressionStopPolicy() {}

    public static Reason reason(CombatEncounter encounter, int completedSteps, int maxSteps) {
        if (encounter == null) throw new IllegalArgumentException("encounter must not be null");
        if (completedSteps < 0 || maxSteps < 1) throw new IllegalArgumentException("invalid progression step bound");
        if (encounter.status() == CombatEncounter.Status.ENDED) return Reason.ENDED;
        if (encounter.status() == CombatEncounter.Status.REACTION_PENDING) return Reason.REACTION_PENDING;
        if (encounter.status() != CombatEncounter.Status.ACTIVE) return Reason.NOT_AI_TURN;
        if (encounter.currentParticipant().controller() == CombatParticipant.Controller.PLAYER) return Reason.HUMAN_TURN;
        if (completedSteps >= maxSteps) return Reason.MAX_STEPS;
        return Reason.CONTINUE;
    }
}
