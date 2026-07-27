package com.dndmaster.adventure.application.scenario;

public final class ScenarioNotReadyException extends RuntimeException {
    public ScenarioNotReadyException() {
        super("scenario is not ready for the AI game master");
    }
}
