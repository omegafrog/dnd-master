package com.dndmaster.aigamemaster.application.ai;

public final class AiExecutionUnavailableException extends RuntimeException {
    private final AiExecutionFailure failure;

    public AiExecutionUnavailableException(AiExecutionFailure failure) {
        super(failure.reason() + ": " + failure.message());
        this.failure = failure;
    }

    public AiExecutionFailure failure() { return failure; }
}
