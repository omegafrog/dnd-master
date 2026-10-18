package com.dndmaster.adventure.application.scenario.preparation;

/** Marks a GM boundary failure that may succeed when the same request is retried once. */
public final class TransientInitialSituationGmFailureException extends RuntimeException {
    public TransientInitialSituationGmFailureException(Throwable cause) {
        super("initial situation GM request failed transiently", cause);
    }
}
