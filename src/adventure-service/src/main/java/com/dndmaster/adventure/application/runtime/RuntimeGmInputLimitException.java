package com.dndmaster.adventure.application.runtime;

/** Retryable failure when required GM input cannot fit the selected model. */
public class RuntimeGmInputLimitException extends RuntimeException {
    public RuntimeGmInputLimitException(String message) { super(message); }
    public RuntimeGmInputLimitException(String message, Throwable cause) { super(message, cause); }
}
