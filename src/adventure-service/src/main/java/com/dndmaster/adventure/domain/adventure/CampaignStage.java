package com.dndmaster.adventure.domain.adventure;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CampaignStage(
        int order,
        String scene,
        String goal,
        String conflict,
        List<String> cluesAndNpcs,
        String transitionCondition,
        List<UUID> evidenceIds) {
    public CampaignStage {
        if (order <= 0) throw new IllegalArgumentException("stage order must be positive");
        scene = required(scene, "scene");
        goal = required(goal, "goal");
        conflict = required(conflict, "conflict");
        cluesAndNpcs = List.copyOf(Objects.requireNonNull(cluesAndNpcs, "clues and npcs must not be null"));
        if (cluesAndNpcs.isEmpty() || cluesAndNpcs.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("stage must contain source-grounded clues or npcs");
        }
        transitionCondition = required(transitionCondition, "transition condition");
        evidenceIds = List.copyOf(Objects.requireNonNull(evidenceIds, "evidence ids must not be null"));
        if (evidenceIds.isEmpty() || evidenceIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("stage must cite evidence");
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
