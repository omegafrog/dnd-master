package com.dndmaster.aigamemaster.infrastructure.ai;

/** Structured failure raised when a Codex turn does not complete by its hard deadline. */
public final class CodexTurnTimeoutException extends ProviderTimeoutException {
    private final String operationId;
    private final String turnId;
    private final String phase;
    private final String model;
    private final String reasoning;
    private final long elapsedMillis;
    private final long timeoutMillis;
    private final String lastEvent;

    public CodexTurnTimeoutException(String operationId, String turnId, String phase, String model,
            String reasoning, long elapsedMillis, long timeoutMillis, String lastEvent, Throwable cause) {
        super("Codex turn timed out: operationId=" + operationId + ", turnId=" + turnId
                + ", phase=" + phase + ", lastEvent=" + lastEvent, cause);
        this.operationId = operationId;
        this.turnId = turnId;
        this.phase = phase;
        this.model = model;
        this.reasoning = reasoning;
        this.elapsedMillis = elapsedMillis;
        this.timeoutMillis = timeoutMillis;
        this.lastEvent = lastEvent;
    }

    public String operationId() { return operationId; }
    public String turnId() { return turnId; }
    public String phase() { return phase; }
    public String model() { return model; }
    public String reasoning() { return reasoning; }
    public long elapsedMillis() { return elapsedMillis; }
    public long timeoutMillis() { return timeoutMillis; }
    public String lastEvent() { return lastEvent; }
}
