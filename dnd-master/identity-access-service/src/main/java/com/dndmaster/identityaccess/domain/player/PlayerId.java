package com.dndmaster.identityaccess.domain.player;

import java.util.Objects;
import java.util.UUID;

public final class PlayerId {
    private final UUID value;

    private PlayerId(UUID value) {
        this.value = Objects.requireNonNull(value, "value must not be null");
    }

    static PlayerId fromAuthenticatedSubject(String subject) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("authenticated subject must not be blank");
        }
        try {
            return new PlayerId(UUID.fromString(subject.trim()));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("authenticated subject must be a UUID", exception);
        }
    }

    public String value() {
        return value.toString();
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate == this || candidate instanceof PlayerId other && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "PlayerId[" + value + "]";
    }
}
