package com.dndmaster.adventure.application.progress;

public final class CrossContextTimeoutException extends RuntimeException {
    public CrossContextTimeoutException(Throwable cause) {
        super("AI Game Master bounded context timed out", cause);
    }
}
