package com.dndmaster.adventure.application.saved;

public final class AdventureNotFoundException extends RuntimeException {
    public AdventureNotFoundException() { super("adventure was not found"); }
}
