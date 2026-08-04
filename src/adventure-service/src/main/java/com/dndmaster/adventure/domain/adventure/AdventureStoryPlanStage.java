package com.dndmaster.adventure.domain.adventure;

import java.util.List;
import java.util.Objects;

public record AdventureStoryPlanStage(
        int position,
        String title,
        String goal,
        String conflict,
        String transitionCondition,
        List<String> npcOrClues,
        List<String> endingIds) {
    public AdventureStoryPlanStage {
        if (position < 1) throw new IllegalArgumentException("stage position must be positive");
        title = required(title, "stage title");
        goal = required(goal, "stage goal");
        conflict = required(conflict, "stage conflict");
        transitionCondition = required(transitionCondition, "stage transition condition");
        npcOrClues = List.copyOf(Objects.requireNonNull(npcOrClues));
        endingIds = List.copyOf(Objects.requireNonNull(endingIds));
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
