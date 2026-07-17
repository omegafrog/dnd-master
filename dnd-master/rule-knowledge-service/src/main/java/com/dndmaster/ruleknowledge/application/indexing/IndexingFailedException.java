package com.dndmaster.ruleknowledge.application.indexing;

public final class IndexingFailedException extends RuntimeException {
    public IndexingFailedException(Throwable cause) {
        super("embedding failed; index remains retryable", cause);
    }
}
