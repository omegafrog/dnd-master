package com.dndmaster.identityaccess.application;

public final class UnauthenticatedAccessException extends RuntimeException {
    public UnauthenticatedAccessException() {
        super("authentication is required");
    }
}
