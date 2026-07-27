package com.dndmaster.adventure.domain.adventure;

public final class AdventureDeletedException extends RuntimeException {
    public AdventureDeletedException() { super("deleted adventure cannot be used"); }
}
