package com.dndmaster.combatmap;

import static org.junit.jupiter.api.Assertions.assertNull;

import com.dndmaster.combatmap.api.CombatMapController.PrepareRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatMapPreparationRequestTest {
    @Test
    void omitted_spawn_coordinates_remain_absent_for_context_resolution() {
        PrepareRequest request = new PrepareRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "map", "page-1", null, null);

        assertNull(request.playerSpawnX());
        assertNull(request.playerSpawnY());
    }
}
