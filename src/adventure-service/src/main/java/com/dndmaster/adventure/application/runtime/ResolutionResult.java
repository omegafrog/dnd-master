package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.scenario.ScenarioResolutionUnit;

public record ResolutionResult(ScenarioResolutionUnit unit, int total, boolean success) {
    public ResolutionResult {
        if (total < 1) throw new IllegalArgumentException("resolution total must be positive");
    }
}
