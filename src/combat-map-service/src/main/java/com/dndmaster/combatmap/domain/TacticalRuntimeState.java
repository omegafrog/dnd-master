package com.dndmaster.combatmap.domain;

/** Durable, GM-facing state produced by planned tactical triggers. */
public record TacticalRuntimeState(
        boolean combatEntered,
        boolean alarmRaised,
        boolean reinforcementsActivated,
        boolean bossActivated,
        boolean rewardDiscovered,
        String outcome,
        String transitionId) {
    public TacticalRuntimeState {
        outcome = outcome == null ? "" : outcome.trim();
        transitionId = transitionId == null ? "" : transitionId.trim();
    }
    public static TacticalRuntimeState initial() { return new TacticalRuntimeState(false, false, false, false, false, "", ""); }
}
