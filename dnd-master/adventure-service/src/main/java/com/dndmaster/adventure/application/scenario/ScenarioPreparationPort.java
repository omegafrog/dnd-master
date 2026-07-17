package com.dndmaster.adventure.application.scenario;

import com.dndmaster.adventure.domain.scenario.ScenarioSource;

public interface ScenarioPreparationPort {
    void prepare(ScenarioSource source);
}
