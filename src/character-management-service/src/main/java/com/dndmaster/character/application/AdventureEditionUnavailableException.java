package com.dndmaster.character.application;

public final class AdventureEditionUnavailableException extends RuntimeException {
    public AdventureEditionUnavailableException() { super("adventure edition could not be retrieved"); }
}
