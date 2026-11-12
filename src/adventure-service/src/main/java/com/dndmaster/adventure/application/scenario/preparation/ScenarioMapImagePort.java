package com.dndmaster.adventure.application.scenario.preparation;

import com.dndmaster.adventure.domain.scenario.MapDefinition;

@FunctionalInterface
public interface ScenarioMapImagePort {
    String load(MapDefinition map);
}
