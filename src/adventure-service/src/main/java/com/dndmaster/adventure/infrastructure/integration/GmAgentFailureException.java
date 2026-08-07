package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.runtime.GmAgentFailure;

public final class GmAgentFailureException extends RuntimeException {
    private final GmAgentFailure failure;

    public GmAgentFailureException(GmAgentFailure failure) {
        super(failure.safeMessage());
        this.failure = failure;
    }

    public GmAgentFailure failure() { return failure; }
}
