package com.dndmaster.adventure.application.progress;

public final class AdventureProgressNotFoundException extends RuntimeException {
    public AdventureProgressNotFoundException() {
        super("adventure was not found");
    }
}
