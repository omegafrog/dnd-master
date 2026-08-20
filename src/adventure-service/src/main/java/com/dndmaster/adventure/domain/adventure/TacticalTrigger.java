package com.dndmaster.adventure.domain.adventure;

import java.util.List;
import java.util.Objects;

public record TacticalTrigger(String id, TacticalTriggerType type, List<String> targetIds, String transitionId,
        PlacementGrounding grounding, String qualifyingAction) {
    public TacticalTrigger(String id, TacticalTriggerType type, List<String> targetIds, String transitionId,
            PlacementGrounding grounding) {
        this(id, type, targetIds, transitionId, grounding,
                type == null ? null : type.name().toLowerCase(java.util.Locale.ROOT));
    }
    public TacticalTrigger {
        id = required(id, "trigger id");
        type = Objects.requireNonNull(type, "trigger type must not be null");
        targetIds = List.copyOf(Objects.requireNonNull(targetIds, "trigger targets must be explicit"));
        if (targetIds.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("trigger target id must not be blank");
        }
        transitionId = transitionId == null ? "" : transitionId.trim();
        grounding = Objects.requireNonNull(grounding, "trigger grounding must not be null");
        qualifyingAction = qualifyingAction == null ? null : qualifyingAction.trim();
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
