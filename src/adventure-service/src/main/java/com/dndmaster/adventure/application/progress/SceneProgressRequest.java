package com.dndmaster.adventure.application.progress;

import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import java.util.Objects;

public record SceneProgressRequest(ScenarioId scenarioId, AdventureContext currentContext) {
    public SceneProgressRequest {
        Objects.requireNonNull(scenarioId, "scenario id must not be null");
        Objects.requireNonNull(currentContext, "current context must not be null");
    }
}
