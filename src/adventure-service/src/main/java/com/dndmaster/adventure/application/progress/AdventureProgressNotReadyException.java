package com.dndmaster.adventure.application.progress;

public final class AdventureProgressNotReadyException extends RuntimeException {
    public AdventureProgressNotReadyException() {
        super("scenario, rule set, and character must be ready");
    }
}
