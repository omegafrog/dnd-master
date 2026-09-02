package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.scenario.ScenarioPackage;

public interface TriggerDetectionPort {
    TriggerDetection detect(TriggerInput input, ScenarioPackage scenarioPackage);
}
