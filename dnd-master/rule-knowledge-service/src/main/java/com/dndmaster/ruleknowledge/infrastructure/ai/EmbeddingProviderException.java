package com.dndmaster.ruleknowledge.infrastructure.ai;

public final class EmbeddingProviderException extends RuntimeException {
    public EmbeddingProviderException(Throwable cause) {
        super("Rule knowledge embedding provider failed", cause);
    }
}
