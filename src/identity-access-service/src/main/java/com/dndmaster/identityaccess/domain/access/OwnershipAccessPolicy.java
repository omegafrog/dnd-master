package com.dndmaster.identityaccess.domain.access;

import com.dndmaster.identityaccess.domain.player.AuthenticatedPlayer;
import com.dndmaster.identityaccess.domain.player.OwnerPlayerId;
import java.util.Objects;

public final class OwnershipAccessPolicy {
    public void authorize(AuthenticatedPlayer authenticatedPlayer, OwnerPlayerId ownerPlayerId) {
        Objects.requireNonNull(authenticatedPlayer, "authenticatedPlayer must not be null");
        Objects.requireNonNull(ownerPlayerId, "ownerPlayerId must not be null");
        if (!ownerPlayerId.identifies(authenticatedPlayer.identify())) {
            throw new OwnershipMismatchException();
        }
    }
}
