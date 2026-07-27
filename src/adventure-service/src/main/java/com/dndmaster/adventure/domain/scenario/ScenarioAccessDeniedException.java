package com.dndmaster.adventure.domain.scenario;

public final class ScenarioAccessDeniedException extends RuntimeException {
    public ScenarioAccessDeniedException() {
        super("scenario is not owned by requesting player");
    }
}
