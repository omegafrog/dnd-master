package com.dndmaster.adventure.application.progress;

public final class ScenarioProgressViolationException extends RuntimeException {
    public ScenarioProgressViolationException() {
        super("AI progress is outside the selected scenario");
    }
}
