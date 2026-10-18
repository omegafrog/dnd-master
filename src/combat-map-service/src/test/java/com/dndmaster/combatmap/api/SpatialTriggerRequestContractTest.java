package com.dndmaster.combatmap.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.combatmap.domain.SpatialTrigger;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class SpatialTriggerRequestContractTest {
    @Test
    void accepts_each_supported_trigger_and_maps_the_known_rulebook_phrase() {
        List<String> supported = Arrays.stream(SpatialTrigger.values()).map(Enum::name).toList();

        assertEquals(java.util.EnumSet.allOf(SpatialTrigger.class), CombatMapController.requestTriggers(
                java.util.stream.Stream.concat(supported.stream(), java.util.stream.Stream.of("stepped-on")).toList()));
        assertEquals(SpatialTrigger.ENTER_CELL, CombatMapController.requestTriggers(List.of("enter_cell")).iterator().next());
    }

    @Test
    void rejects_unknown_trigger_text_at_the_service_boundary() {
        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> CombatMapController.requestTriggers(List.of("teleports-nearby")));

        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, failure.getStatusCode());
    }
}
