package com.dndmaster.adventure.application.session;

public final class AdventureAiRequestInProgressException extends RuntimeException {
    public AdventureAiRequestInProgressException() {
        super("AI_REQUEST_ALREADY_IN_PROGRESS");
    }
}
