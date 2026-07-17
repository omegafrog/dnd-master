package com.dndmaster.adventure.application.scenario;

public final class ScenarioNotFoundException extends RuntimeException {
    public ScenarioNotFoundException() {
        super("scenario was not found");
    }
}
