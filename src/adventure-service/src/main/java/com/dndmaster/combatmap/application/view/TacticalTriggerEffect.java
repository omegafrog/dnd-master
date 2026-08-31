package com.dndmaster.combatmap.application.view;

import java.util.List;
import java.util.Objects;

/** A validated Adventure trigger translated into a mutable Combat Map command. */
public record TacticalTriggerEffect(String triggerId, Kind kind, List<String> targetIds, boolean planned, String transitionId, String qualifyingAction) {
    public TacticalTriggerEffect(String triggerId, Kind kind, List<String> targetIds, boolean planned) {
        this(triggerId, kind, targetIds, planned, "", null);
    }
    public TacticalTriggerEffect(String triggerId, Kind kind, List<String> targetIds, boolean planned, String transitionId) {
        this(triggerId, kind, targetIds, planned, transitionId, null);
    }
    public TacticalTriggerEffect {
        if (triggerId == null || triggerId.isBlank()) throw new IllegalArgumentException("trigger id required");
        kind = Objects.requireNonNull(kind, "trigger kind required");
        targetIds = List.copyOf(Objects.requireNonNull(targetIds, "trigger targets required"));
        transitionId = transitionId == null ? "" : transitionId.trim();
        qualifyingAction = qualifyingAction == null ? "" : qualifyingAction.trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
        if (planned && qualifyingAction.isBlank()) throw new IllegalArgumentException("planned trigger qualifying action required");
    }
    public static TacticalTriggerEffect planned(String id, Kind kind, List<String> targets) { return new TacticalTriggerEffect(id, kind, targets, true); }
    public static TacticalTriggerEffect planned(String id, Kind kind, List<String> targets, String transitionId) { return new TacticalTriggerEffect(id, kind, targets, true, transitionId); }
    public static TacticalTriggerEffect planned(String id, Kind kind, List<String> targets, String transitionId, String qualifyingAction) { return new TacticalTriggerEffect(id, kind, targets, true, transitionId, qualifyingAction); }
    public static TacticalTriggerEffect unplanned(String id) { return new TacticalTriggerEffect(id, Kind.COMBAT_ENTRY, List.of(), false); }
    public enum Kind { COMBAT_ENTRY, ALARM, REINFORCEMENT, BOSS, REWARD, FOG_REVEAL, SUCCESS, FAILURE, EXIT, SURRENDER }
}
