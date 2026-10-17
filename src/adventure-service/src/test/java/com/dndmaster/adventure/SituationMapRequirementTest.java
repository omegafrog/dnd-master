package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.domain.scenario.SituationDefinition;
import com.dndmaster.adventure.domain.scenario.StageIntent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SituationMapRequirementTest {
    @Test
    void mapNeedIsAnExplicitSavedSituationAttribute() {
        var mapId = UUID.randomUUID();
        var mapless = situation(null, false);
        var mapNeeded = situation(mapId, true);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var persisted = mapper.convertValue(mapper.valueToTree(mapNeeded), SituationDefinition.class);

        assertFalse(mapless.mapNeeded());
        assertTrue(persisted.mapNeeded());
        org.junit.jupiter.api.Assertions.assertEquals(mapId, persisted.mapDefinitionId());
        assertThrows(IllegalArgumentException.class, () -> situation(null, true));
    }

    @Test
    void olderSavedSituationsInferMapNeedFromTheirMapIdButExplicitFalseWins() {
        var mapId = UUID.randomUUID();
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var olderSavedSituation = mapper.valueToTree(situation(mapId, true));
        ((com.fasterxml.jackson.databind.node.ObjectNode) olderSavedSituation).remove("mapNeeded");
        var explicitMaplessSituation = mapper.valueToTree(situation(mapId, false));

        assertTrue(mapper.convertValue(olderSavedSituation, SituationDefinition.class).mapNeeded());
        assertFalse(mapper.convertValue(explicitMaplessSituation, SituationDefinition.class).mapNeeded());
    }

    private static SituationDefinition situation(UUID mapId, boolean mapNeeded) {
        return new SituationDefinition("opening", List.of(StageIntent.REVELATION), List.of("truth"),
                List.of(), List.of(), List.of(), List.of(), true, true, List.of(),
                com.dndmaster.adventure.domain.scenario.SituationProvenance.GM_SUPPLEMENTED,
                mapId, List.of(), mapNeeded);
    }
}
