package com.dndmaster.adventure.application.runtime;

import java.util.Objects;

/** Player prose may describe an authoritative result, but cannot produce one. */
public record NarrationContract(AuthoritativeResolution resolution, String narration) {
    public NarrationContract {
        resolution = Objects.requireNonNull(resolution, "resolution must not be null");
        if (resolution.status() != AuthoritativeResolution.Status.RESOLVED) {
            throw new IllegalStateException("narration requires a resolved authoritative outcome");
        }
        narration = required(narration, "narration");
    }

    public static NarrationContract from(AuthoritativeResolution resolution, String narration) {
        return new NarrationContract(resolution, narration);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
