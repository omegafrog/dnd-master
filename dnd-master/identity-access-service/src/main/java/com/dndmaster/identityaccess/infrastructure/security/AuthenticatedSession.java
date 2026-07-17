package com.dndmaster.identityaccess.infrastructure.security;

import java.util.Objects;

public record AuthenticatedSession(String token, String playerId) {
    public AuthenticatedSession {
        Objects.requireNonNull(token, "token must not be null");
        Objects.requireNonNull(playerId, "playerId must not be null");
    }
}
