package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.scenario.MapDefinition;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.StoryMapBinding;
import java.util.Objects;
import java.util.Optional;

/** Selects a player-safe map; absence is a supported text-only runtime state. */
public final class TacticalMapActivationPolicy {
    public Activation decide(ScenarioPackage scenarioPackage, String stage, String location, String condition) {
        Objects.requireNonNull(scenarioPackage, "scenario package must not be null");
        return scenarioPackage.storyMapBindings().stream()
                .filter(binding -> binding.matches(stage, location, condition))
                .map(binding -> scenarioPackage.mapDefinitions().stream()
                        .filter(map -> map.id().equals(binding.mapDefinitionId()))
                        .findFirst().map(map -> map.autoActivatable() ? new Activation(Optional.of(map), false) : new Activation(Optional.empty(), true)))
                .flatMap(Optional::stream).findFirst().orElse(new Activation(Optional.empty(), false));
    }

    public record Activation(Optional<MapDefinition> map, boolean textFallback) {
        public Activation { map = Objects.requireNonNull(map, "map must not be null"); }
    }
}
