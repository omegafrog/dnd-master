package com.dndmaster.adventure.application.runtime;

import java.util.Objects;
import java.util.regex.Pattern;

/** Typed identifier for an authoritative fact. Values never belong in the ID. */
public record ReferencedFactId(String value) {
    private static final Pattern CANONICAL = Pattern.compile("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*");

    public ReferencedFactId {
        value = Objects.requireNonNull(value, "fact id must not be null").trim();
        if (!CANONICAL.matcher(value).matches()) throw new IllegalArgumentException("invalid authoritative fact id");
    }
}
