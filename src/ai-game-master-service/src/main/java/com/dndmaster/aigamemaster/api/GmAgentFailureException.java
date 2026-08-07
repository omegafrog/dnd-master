package com.dndmaster.aigamemaster.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class GmAgentFailureException extends ResponseStatusException {
    private final GmFailure failure;

    public GmAgentFailureException(GmFailure failure, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, failure.safeMessage(), cause);
        this.failure = failure;
    }

    public GmFailure failure() { return failure; }
}
