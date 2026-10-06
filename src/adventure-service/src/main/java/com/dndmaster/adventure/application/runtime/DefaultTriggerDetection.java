package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.scenario.ScenarioPackage;

public final class DefaultTriggerDetection implements TriggerDetectionPort {
    @Override
    public TriggerDetection detect(TriggerInput input, ScenarioPackage scenarioPackage) {
        return TriggerDetection.none();
    }
}
