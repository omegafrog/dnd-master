package com.dndmaster.adventure.application.runtime;

public class RequiredToolFailureException extends RuntimeException {
    public RequiredToolFailureException(String toolName) { super("required GM tool failed: " + toolName); }
}
