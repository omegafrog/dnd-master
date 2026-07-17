package com.dndmaster.identityaccess.application;

import com.dndmaster.identityaccess.domain.access.OwnershipAccessPolicy;
import com.dndmaster.identityaccess.domain.player.AuthenticatedPlayer;
import com.dndmaster.identityaccess.domain.player.OwnerPlayerId;
import com.dndmaster.identityaccess.domain.player.Player;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class PlayerAccessApplicationService {
    private final OwnershipAccessPolicy ownershipAccessPolicy;

    public PlayerAccessApplicationService(OwnershipAccessPolicy ownershipAccessPolicy) {
        this.ownershipAccessPolicy = Objects.requireNonNull(ownershipAccessPolicy, "ownershipAccessPolicy must not be null");
    }

    public AuthenticatedPlayer login(Optional<String> verifiedSubject) {
        String subject = Objects.requireNonNull(verifiedSubject, "verifiedSubject must not be null")
                .filter(value -> !value.isBlank())
                .orElseThrow(UnauthenticatedAccessException::new);
        return AuthenticatedPlayer.fromVerifiedSubject(subject);
    }

    public Player establishPlayer(AuthenticatedPlayer authenticatedPlayer) {
        return Player.establish(Objects.requireNonNull(authenticatedPlayer, "authenticatedPlayer must not be null"));
    }

    public <T> T provideOwnedResource(
            Optional<AuthenticatedPlayer> authenticatedPlayer,
            OwnerPlayerId ownerPlayerId,
            Supplier<T> resourceLoader) {
        AuthenticatedPlayer player = Objects.requireNonNull(authenticatedPlayer, "authenticatedPlayer must not be null")
                .orElseThrow(UnauthenticatedAccessException::new);
        ownershipAccessPolicy.authorize(player, ownerPlayerId);
        return Objects.requireNonNull(resourceLoader, "resourceLoader must not be null").get();
    }
}
