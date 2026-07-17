package com.dndmaster.adventure.application.scenario;

public final class ScenarioPreparationFailedException extends RuntimeException {
    public ScenarioPreparationFailedException(Throwable cause) {
        super("scenario preparation failed", cause);
    }
}
