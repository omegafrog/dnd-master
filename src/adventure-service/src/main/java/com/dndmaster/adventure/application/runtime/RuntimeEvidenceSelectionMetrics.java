package com.dndmaster.adventure.application.runtime;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public record RuntimeEvidenceSelectionMetrics(
        int selectedCount,
        Map<RuntimeEvidenceType, Integer> selectedByType,
        String stageKey,
        String actionIntent) {
    public RuntimeEvidenceSelectionMetrics {
        if (selectedCount < 0 || selectedCount > 8) throw new IllegalArgumentException("selected evidence count must be between zero and eight");
        EnumMap<RuntimeEvidenceType, Integer> copy = new EnumMap<>(RuntimeEvidenceType.class);
        copy.putAll(Objects.requireNonNull(selectedByType, "selected evidence metrics must not be null"));
        selectedByType = Map.copyOf(copy);
        stageKey = required(stageKey, "stage key");
        actionIntent = required(actionIntent, "action intent");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
