package com.dndmaster.adventure.application.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.domain.runtime.PendingMapMovementConfirmation;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PendingMapMovementConfirmationTest {
    @Test
    void retains_the_full_preview_needed_to_restore_confirmation_after_reconnect() {
        UUID adventureId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID mapId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        var path = List.of(new PendingMapMovementConfirmation.Position(1, 1),
                new PendingMapMovementConfirmation.Position(2, 1));
        var waypoints = List.of(new PendingMapMovementConfirmation.Position(1, 1));

        var pending = new PendingMapMovementConfirmation(adventureId, ownerId, mapId, tokenId,
                7, path, 5, "preview-fingerprint", waypoints);

        assertEquals(adventureId, pending.adventureId());
        assertEquals(ownerId, pending.ownerPlayerId());
        assertEquals(path, pending.path());
        assertEquals(waypoints, pending.waypoints());
        assertEquals("preview-fingerprint", pending.fingerprint());
    }
}
