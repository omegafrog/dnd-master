package com.dndmaster.combatmap.application.movement;

import java.util.List;
import java.util.Objects;

/** Public facts for a normal movement stop before the next cell is entered. */
public record MovementInterruption(String reason, List<String> publicEvents) {
    public MovementInterruption {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("movement interruption reason must not be blank");
        reason = reason.trim();
        publicEvents = List.copyOf(Objects.requireNonNull(publicEvents, "movement interruption events must not be null"));
    }
}
