package com.dndmaster.ruleknowledge.application.pipeline;

public final class RulebookPipelineException extends RuntimeException {
    public RulebookPipelineException(String message) {
        super(message);
    }

    public RulebookPipelineException(String message, Throwable cause) {
        super(message, cause);
    }
}
