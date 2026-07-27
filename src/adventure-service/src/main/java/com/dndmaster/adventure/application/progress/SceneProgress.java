package com.dndmaster.adventure.application.progress;

import com.dndmaster.adventure.domain.adventure.ScenarioId;
import java.util.Objects;

public record SceneProgress(ScenarioId scenarioId, String scene, String npcState) {
    public SceneProgress {
        Objects.requireNonNull(scenarioId, "scenario id must not be null");
        scene = required(scene, "scene");
        npcState = optional(npcState);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
