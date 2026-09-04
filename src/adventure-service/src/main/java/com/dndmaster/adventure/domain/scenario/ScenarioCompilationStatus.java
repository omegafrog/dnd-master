package com.dndmaster.adventure.domain.scenario;

public enum ScenarioCompilationStatus {
    QUEUED,
    PROCESSING,
    COMPLETED,
    BLOCKED,
    REQUESTED,
    RUNNING,
    WAITING_RETRY,
    PUBLISHED,
    FAILED
}
