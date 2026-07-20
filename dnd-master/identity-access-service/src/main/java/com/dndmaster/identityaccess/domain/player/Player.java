package com.dndmaster.identityaccess.domain.player;

import java.util.Objects;

public final class Player {
    private final PlayerId id;
    private final PlayerStatus status;

    private Player(PlayerId id, PlayerStatus status) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    public static Player establish(AuthenticatedPlayer authenticatedPlayer) {
        Objects.requireNonNull(authenticatedPlayer, "authenticatedPlayer must not be null");
        return new Player(authenticatedPlayer.identify(), PlayerStatus.ACTIVE);
    }

    public PlayerId id() {
        return id;
    }

    public PlayerStatus status() {
        return status;
    }
}
