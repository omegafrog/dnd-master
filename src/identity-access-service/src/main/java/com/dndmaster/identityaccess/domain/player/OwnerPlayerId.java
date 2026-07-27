package com.dndmaster.identityaccess.domain.player;

import java.util.Objects;
import java.util.UUID;

public final class OwnerPlayerId {
    private final UUID value;

    private OwnerPlayerId(UUID value) {
        this.value = Objects.requireNonNull(value, "value must not be null");
    }

    public static OwnerPlayerId fromStoredValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("owner player id must not be blank");
        }
        try {
            return new OwnerPlayerId(UUID.fromString(value.trim()));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("owner player id must be a UUID", exception);
        }
    }

    public boolean identifies(PlayerId playerId) {
        return value.toString().equals(Objects.requireNonNull(playerId, "playerId must not be null").value());
    }

    public String value() {
        return value.toString();
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate == this || candidate instanceof OwnerPlayerId other && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
