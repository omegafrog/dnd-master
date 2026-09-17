package com.dndmaster.adventure.infrastructure.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.domain.scenario.ScenarioResolutionDetail;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CrossContextHttpResolutionExtractionGatewayTest {
    @Test
    void marksScenarioCompilationResolutionExtractionWithoutCreatingAuthoringCall() {
        String operationId = CrossContextHttpResolutionExtractionGateway.operationId("compilation-1", false);

        assertEquals("scenario-compilation:compilation-1:resolution-candidates", operationId);
        assertTrue(operationId.contains(":resolution-candidates"));
        assertTrue(!operationId.contains("authoring"));
    }

    @Test
    void marksCandidateRepairAsTheOnlyAdditionalScenarioCompilationProviderCall() {
        assertEquals("scenario-compilation:compilation-1:resolution-candidate-repair",
                CrossContextHttpResolutionExtractionGateway.operationId("compilation-1", true));
    }

    @Test
    void normalizesProviderCombatEventToWorldEventAtTheResponseBoundary() throws Exception {
        String response = "{\"trigger\":{\"type\":\"COMBAT_EVENT\",\"condition\":\"combat begins\"}}";

        var detail = new ObjectMapper().readValue(response, ScenarioResolutionDetail.class);

        assertEquals(ScenarioResolutionDetail.TriggerType.WORLD_EVENT, detail.trigger().type());
    }
}
