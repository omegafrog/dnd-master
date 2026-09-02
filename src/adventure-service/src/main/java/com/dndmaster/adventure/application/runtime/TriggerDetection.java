package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.scenario.ScenarioResolutionDetail;
import com.dndmaster.adventure.domain.scenario.ScenarioResolutionUnit;

import java.util.Optional;

public record TriggerDetection(ScenarioResolutionDetail.TriggerType type, String reason,
                               ScenarioResolutionUnit unit) {
    public TriggerDetection {
        reason = reason == null ? "" : reason.trim();
    }

    public static TriggerDetection none() {
        return new TriggerDetection(null, "no matching trigger", null);
    }

    public boolean detected() { return unit != null; }
    public Optional<ScenarioResolutionUnit> candidate() { return Optional.ofNullable(unit); }
}
