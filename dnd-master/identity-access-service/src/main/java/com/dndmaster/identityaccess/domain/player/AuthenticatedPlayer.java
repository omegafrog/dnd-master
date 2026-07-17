package com.dndmaster.identityaccess.domain.player;

import java.util.Objects;

public final class AuthenticatedPlayer {
    private final PlayerId playerId;

    private AuthenticatedPlayer(PlayerId playerId) {
        this.playerId = Objects.requireNonNull(playerId, "playerId must not be null");
    }

    public static AuthenticatedPlayer fromVerifiedSubject(String subject) {
        return new AuthenticatedPlayer(PlayerId.fromAuthenticatedSubject(subject));
    }

    public PlayerId identify() {
        return playerId;
    }
}
