package com.dndmaster.adventure.domain.scenario;

public final class ScenarioBundleDeletionConflictException extends IllegalStateException {
    public ScenarioBundleDeletionConflictException() {
        super("scenario bundle is referenced by an active adventure");
    }
}
