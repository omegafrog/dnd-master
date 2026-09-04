package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.AdventureContext;
import java.util.List;
import java.util.Objects;

/** Planner-only input. Writer adapters must not reuse this unrestricted context. */
public record PlannerContext(
        AdventureContext currentContext,
        String action,
        EvidencePack evidencePack,
        List<String> recentTurns,
        List<String> characterSnapshots,
        String scenarioContext) {
    public PlannerContext {
        currentContext = Objects.requireNonNull(currentContext, "current context must not be null");
        action = required(action, "action");
        evidencePack = Objects.requireNonNull(evidencePack, "evidence pack must not be null");
        recentTurns = List.copyOf(Objects.requireNonNull(recentTurns, "recent turns must not be null"));
        characterSnapshots = List.copyOf(Objects.requireNonNull(characterSnapshots, "character snapshots must not be null"));
        scenarioContext = scenarioContext == null ? "" : scenarioContext.trim();
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
