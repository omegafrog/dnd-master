package com.dndmaster.identityaccess.infrastructure.security;

import java.util.Objects;

public record PlayerPrincipal(String playerId, String username) {
    public PlayerPrincipal {
        Objects.requireNonNull(playerId, "playerId must not be null");
        Objects.requireNonNull(username, "username must not be null");
    }
}
