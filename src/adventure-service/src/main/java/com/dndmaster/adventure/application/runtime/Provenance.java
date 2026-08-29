package com.dndmaster.adventure.application.runtime;

import java.util.Objects;

/** Provenance kept with context items so style guidance cannot become evidence. */
public record Provenance(String sourceId, String purpose, String version) {
    public Provenance {
        sourceId = required(sourceId, "source id");
        purpose = required(purpose, "purpose");
        version = required(version, "version");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
