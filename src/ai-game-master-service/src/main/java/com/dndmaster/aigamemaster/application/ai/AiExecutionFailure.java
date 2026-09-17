package com.dndmaster.aigamemaster.application.ai;

public record AiExecutionFailure(Reason reason, String message) implements AiExecutionResult {
    public enum Reason { CONNECTION_UNAVAILABLE, CONNECTION_LOST, TIMEOUT, DELIVERY_FAILED, LOCAL_EXECUTION_FAILED }

    public AiExecutionFailure {
        if (reason == null) throw new IllegalArgumentException("failure reason is required");
        if (message == null || message.isBlank()) throw new IllegalArgumentException("failure message is required");
        message = message.trim();
    }

    @Override public String finalText() { return ""; }
}
