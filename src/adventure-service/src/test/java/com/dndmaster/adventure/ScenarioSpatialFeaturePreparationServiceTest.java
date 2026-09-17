package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.scenario.preparation.ScenarioSpatialFeaturePlacementModelPort;
import com.dndmaster.adventure.application.scenario.preparation.ScenarioSpatialFeaturePreparationService;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.MapDefinition;
import com.dndmaster.adventure.domain.scenario.MapSafetyStatus;
import com.dndmaster.adventure.domain.scenario.MapSourceReference;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScenarioSpatialFeaturePreparationServiceTest {
    @Test
    void sends_only_validated_structured_batch_facts_to_the_map_boundary() {
        UUID featureId = UUID.randomUUID();
        MapDefinition map = map(featureId, true, "MAGICAL_AREA_EFFECT", List.of("2,2"), 3, "EXPIRE", true);

        var result = new ScenarioSpatialFeaturePreparationService(context ->
                new ScenarioSpatialFeaturePlacementModelPort.Proposal(List.of(
                        new ScenarioSpatialFeaturePlacementModelPort.Candidate(featureId, "MAGICAL_AREA_EFFECT", List.of("2,2"), true))))
                .prepare(map);

        assertTrue(result.activationAllowed());
        assertEquals(1, result.placements().size());
        var placement = result.placements().getFirst();
        assertEquals(map.source().knowledgeDocumentId().value(), placement.evidence().sourceDocumentId());
        assertEquals(List.of("2,2"), placement.evidence().allowedCells());
        assertEquals(3, placement.durationTurns());
        assertEquals("EXPIRE", placement.removalPolicy());
        assertTrue(placement.overlapAllowed());
    }

    @Test
    void unsupported_coordinates_block_after_three_attempts_without_returning_hidden_candidates() {
        UUID featureId = UUID.randomUUID();
        MapDefinition map = map(featureId, true, "TRAP", List.of("2,2"), -1, "", false);

        var result = new ScenarioSpatialFeaturePreparationService(context ->
                new ScenarioSpatialFeaturePlacementModelPort.Proposal(List.of(
                        new ScenarioSpatialFeaturePlacementModelPort.Candidate(featureId, "TRAP", List.of("9,9"), true))))
                .prepare(map);

        assertFalse(result.activationAllowed());
        assertEquals(3, result.attempts());
        assertTrue(result.placements().isEmpty());
        assertTrue(result.failures().stream().allMatch(message -> !message.contains("9,9")));
    }

    private static MapDefinition map(UUID featureId, boolean required, String type, List<String> cells,
            int duration, String removal, boolean overlap) {
        return new MapDefinition(UUID.randomUUID(), "map", "page-1",
                new MapDefinition.MapGrid(0, 0, 50, 0, "5 ft"), List.of(), List.of(), List.of(),
                new MapSourceReference(new KnowledgeDocumentId(UUID.randomUUID()), 7, "asset:map-1"),
                .95, MapSafetyStatus.SAFE,
                List.of(new MapDefinition.SpatialFeatureRequirement(featureId, type, required,
                        List.of("source-section:trap"), cells, "rulebook:perception", 15, "PASSIVE",
                        List.of("ENTER_CELL"), duration, removal, overlap)));
    }
}
