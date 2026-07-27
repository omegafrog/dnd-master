package com.dndmaster.adventure.domain.scenario;

public enum ScenarioCompilationStatus {
    REQUESTED,
    RUNNING,
    WAITING_RETRY,
    PUBLISHED,
    FAILED
}
