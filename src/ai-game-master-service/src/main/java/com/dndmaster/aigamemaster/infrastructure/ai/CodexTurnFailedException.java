package com.dndmaster.aigamemaster.infrastructure.ai;

/** Distinguishes a provider-reported turn failure from a client deadline timeout. */
public final class CodexTurnFailedException extends IllegalStateException {
    private final String turnId;
    private final String lastEvent;
    private final String errorCode;
    private final String errorCategory;

    public CodexTurnFailedException(String turnId, String lastEvent, String errorCode, String errorCategory, String message) {
        super(message);
        this.turnId = turnId;
        this.lastEvent = lastEvent;
        this.errorCode = errorCode;
        this.errorCategory = errorCategory;
    }

    public String turnId() { return turnId; }
    public String lastEvent() { return lastEvent; }
    public String errorCode() { return errorCode; }
    public String errorCategory() { return errorCategory; }
}
