package com.dndmaster.ruleknowledge.application.search;

/** Stable error returned when either required candidate retriever fails after its one retry. */
public final class EvidenceSearchUnavailableException extends RuntimeException {
    public EvidenceSearchUnavailableException(Throwable cause) {
        super("Evidence candidate search is unavailable", cause);
    }
}
