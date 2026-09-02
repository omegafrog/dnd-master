package com.dndmaster.adventure.infrastructure.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CrossContextHttpResolutionExtractionGatewayTest {
    @Test
    void marksScenarioCompilationResolutionExtractionWithoutCreatingStoryPlanAuthoringCall() {
        String operationId = CrossContextHttpResolutionExtractionGateway.operationId("compilation-1", false);

        assertEquals("scenario-compilation:compilation-1:resolution-candidates", operationId);
        assertTrue(operationId.contains(":resolution-candidates"));
        assertTrue(!operationId.contains("story-plan"));
    }

    @Test
    void marksCandidateRepairAsTheOnlyAdditionalScenarioCompilationProviderCall() {
        assertEquals("scenario-compilation:compilation-1:resolution-candidate-repair",
                CrossContextHttpResolutionExtractionGateway.operationId("compilation-1", true));
    }
}
