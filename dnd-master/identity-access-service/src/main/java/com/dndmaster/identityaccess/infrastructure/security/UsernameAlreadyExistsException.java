package com.dndmaster.identityaccess.infrastructure.security;

public final class UsernameAlreadyExistsException extends RuntimeException {
    public UsernameAlreadyExistsException(Throwable cause) {
        super("username already exists", cause);
    }
}
