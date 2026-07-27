package com.dndmaster.identityaccess.domain.access;

public final class OwnershipMismatchException extends RuntimeException {
    public OwnershipMismatchException() {
        super("authenticated player does not own the requested resource");
    }
}
