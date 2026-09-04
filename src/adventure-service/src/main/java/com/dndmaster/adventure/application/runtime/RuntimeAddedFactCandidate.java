package com.dndmaster.adventure.application.runtime;

/** Minimal fact proposed only after every authoritative lookup returned NOT_FOUND. */
public record RuntimeAddedFactCandidate(String subject, String content) {
    public RuntimeAddedFactCandidate {
        subject = required(subject, "fact subject");
        content = required(content, "fact content");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
