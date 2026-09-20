package com.dndmaster.adventure.application.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.dndmaster.adventure.domain.runtime.PendingMapMovementConfirmation;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void natural_language_confirmation_keeps_source_and_destination_without_copying_route() {
        var destination = new PendingMapMovementConfirmation.Position(4, 2);
        var pending = new PendingMapMovementConfirmation(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                9, List.of(), 0, "public-preview", List.of(), "북쪽 문으로 가", destination, UUID.randomUUID());

        assertEquals("북쪽 문으로 가", pending.sourceText());
        assertEquals(destination, pending.destination());
        assertEquals(0, pending.path().size());
        assertEquals(9, pending.mapVersion());
        assertNotNull(pending.pendingTurnId());
    }

    @Test
    void terminal_confirmation_requires_the_replayed_result() {
        var path = List.of(new PendingMapMovementConfirmation.Position(1, 1),
                new PendingMapMovementConfirmation.Position(2, 1));
        assertThrows(IllegalArgumentException.class, () -> new PendingMapMovementConfirmation(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 4,
                path, 1, "fingerprint", List.of(), "문으로 가", path.getLast(), UUID.randomUUID(),
                UUID.randomUUID(), true, null));
    }
}
