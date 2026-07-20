package com.dndmaster.identityaccess.infrastructure.persistence;

import java.util.Objects;
import java.util.UUID;

public record StoredCredential(UUID playerId, String username, String passwordHash, boolean active) {
    public StoredCredential {
        Objects.requireNonNull(playerId, "playerId must not be null");
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(passwordHash, "passwordHash must not be null");
    }
}
