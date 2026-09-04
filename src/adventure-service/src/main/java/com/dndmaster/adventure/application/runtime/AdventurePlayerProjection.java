package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.Adventure;
import java.util.List;
import java.util.UUID;

/** Explicit player boundary. Hidden ScenarioModel and canonical runtime snapshots are absent by type. */
public record AdventurePlayerProjection(UUID adventureId, String status, long version, String currentScene,
        List<String> disclosedFactIds) {
    public AdventurePlayerProjection {
        currentScene = currentScene == null ? "" : currentScene;
        disclosedFactIds = List.copyOf(disclosedFactIds == null ? List.of() : disclosedFactIds);
    }

    public static AdventurePlayerProjection from(Adventure adventure) {
        return new AdventurePlayerProjection(adventure.id().value(), adventure.status().name(), adventure.version(),
                adventure.currentContext().currentScene(), adventure.disclosureState().disclosedFactIds().stream().sorted().toList());
    }

}
