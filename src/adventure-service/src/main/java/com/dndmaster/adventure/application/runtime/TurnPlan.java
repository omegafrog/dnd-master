package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.Objects;

/** Planner-owned decision contract. It intentionally has no prose field. */
public record TurnPlan(
        String scene,
        String npcState,
        String judgment,
        List<String> revealableFacts,
        List<String> forbiddenFacts) {
    public TurnPlan {
        scene = required(scene, "scene");
        npcState = npcState == null ? "" : npcState.trim();
        judgment = required(judgment, "judgment");
        revealableFacts = List.copyOf(Objects.requireNonNull(revealableFacts, "revealable facts must not be null"));
        forbiddenFacts = List.copyOf(Objects.requireNonNull(forbiddenFacts, "forbidden facts must not be null"));
    }

    public static TurnPlan from(RuntimePlan plan) {
        Objects.requireNonNull(plan, "runtime plan must not be null");
        return new TurnPlan(plan.scene(), plan.npcState(), plan.judgment(),
                List.of(), List.of());
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
