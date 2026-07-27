package com.dndmaster.identityaccess.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.identityaccess.domain.access.OwnershipAccessPolicy;
import com.dndmaster.identityaccess.domain.access.OwnershipMismatchException;
import com.dndmaster.identityaccess.domain.player.OwnerPlayerId;
import com.dndmaster.identityaccess.domain.player.PlayerId;
import com.dndmaster.identityaccess.domain.player.PlayerStatus;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class PlayerAccessApplicationServiceTest {
    private static final String PLAYER_ID = "9ea8e58c-a099-4b6a-af16-787d1ca4abb2";
    private final PlayerAccessApplicationService service =
            new PlayerAccessApplicationService(new OwnershipAccessPolicy());

    @Test
    void authenticatedSubjectIsTheOnlyPlayerIdCreationPath() {
        var authenticatedPlayer = service.login(Optional.of(PLAYER_ID));
        var player = service.establishPlayer(authenticatedPlayer);

        assertEquals(PLAYER_ID, authenticatedPlayer.identify().value());
        assertEquals(authenticatedPlayer.identify(), player.id());
        assertEquals(PlayerStatus.ACTIVE, player.status());
        assertFalse(Arrays.stream(PlayerId.class.getDeclaredConstructors())
                .anyMatch(constructor -> Modifier.isPublic(constructor.getModifiers())));
    }

    @Test
    void unauthenticatedRequestsAreRejected() {
        assertThrows(UnauthenticatedAccessException.class, () -> service.login(Optional.empty()));
        assertThrows(
                UnauthenticatedAccessException.class,
                () -> service.provideOwnedResource(
                        Optional.empty(), OwnerPlayerId.fromStoredValue(PLAYER_ID), () -> "secret"));
    }

    @Test
    void ownerMismatchIsRejectedBeforeResourceIsLoaded() {
        var authenticatedPlayer = service.login(Optional.of(PLAYER_ID));
        var resourceLoaded = new AtomicBoolean(false);

        assertThrows(
                OwnershipMismatchException.class,
                () -> service.provideOwnedResource(
                        Optional.of(authenticatedPlayer),
                        OwnerPlayerId.fromStoredValue("649da861-e4b0-449a-b42f-9e49073c2ca4"),
                        () -> {
                            resourceLoaded.set(true);
                            return "secret";
                        }));
        assertFalse(resourceLoaded.get());
    }

    @Test
    void ownerCanAccessResource() {
        var authenticatedPlayer = service.login(Optional.of(PLAYER_ID));

        String resource = service.provideOwnedResource(
                Optional.of(authenticatedPlayer), OwnerPlayerId.fromStoredValue(PLAYER_ID), () -> "owned");

        assertEquals("owned", resource);
    }
}
