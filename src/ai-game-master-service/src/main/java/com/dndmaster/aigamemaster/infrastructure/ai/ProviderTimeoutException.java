package com.dndmaster.aigamemaster.infrastructure.ai;
public class ProviderTimeoutException extends RuntimeException{public ProviderTimeoutException(Throwable cause){super("AI provider timeout",cause);} protected ProviderTimeoutException(String message, Throwable cause){super(message,cause);}}
