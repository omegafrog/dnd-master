package com.dndmaster.adventure.application.scenario;

import com.dndmaster.adventure.domain.scenario.ScenarioSource;

public interface ScenarioStoragePort {
    ScenarioSource store(ScenarioUpload upload);
}
