package com.dndmaster.aigamemaster.infrastructure.ai;

/** Distinguishes a provider-reported turn failure from a client deadline timeout. */
public final class CodexTurnFailedException extends IllegalStateException {
    private final String turnId;
    private final String lastEvent;

    public CodexTurnFailedException(String turnId, String lastEvent, String message) {
        super(message);
        this.turnId = turnId;
        this.lastEvent = lastEvent;
    }

    public String turnId() { return turnId; }
    public String lastEvent() { return lastEvent; }
}
