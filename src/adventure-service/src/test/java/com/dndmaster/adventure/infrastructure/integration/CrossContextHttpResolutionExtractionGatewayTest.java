package com.dndmaster.adventure.infrastructure.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.scenario.compilation.ResolutionExtractionPort;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
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
    void sendsTheServerConfirmedSoloPlayerIdToTheResolutionCandidateContract() {
        UUID soloPlayerId = UUID.randomUUID();
        var request = new ResolutionExtractionPort.ResolutionExtractionRequest(soloPlayerId, "compilation-1",
                List.of(new ResolutionExtractionPort.SourceExcerpt(new KnowledgeDocumentId(UUID.randomUUID()), 1,
                        "page:1", "DC 12 Wisdom check")),
                "resolution-candidate-v2", "resolution-prompt-v2");

        var body = new ObjectMapper().valueToTree(CrossContextHttpResolutionExtractionGateway.wireRequest(request));

        assertEquals(soloPlayerId.toString(), body.path("soloPlayerId").asText());
    }
}
