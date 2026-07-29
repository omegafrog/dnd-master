package com.dndmaster.ruleknowledge.application.indexing;

public final class IndexingFailedException extends RuntimeException {
    private final boolean retryable;

    public IndexingFailedException(Throwable cause, boolean retryable) {
        super("embedding failed; retryable=" + retryable, cause);
        this.retryable = retryable;
    }

    public boolean retryable() { return retryable; }
}
