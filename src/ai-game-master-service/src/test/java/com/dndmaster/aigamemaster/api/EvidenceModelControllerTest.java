package com.dndmaster.aigamemaster.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.aigamemaster.application.evidence.EvidenceCandidate;
import com.dndmaster.aigamemaster.application.evidence.EvidenceRerankRequest;
import com.dndmaster.aigamemaster.application.evidence.EvidenceRerankerService;
import com.dndmaster.aigamemaster.application.evidence.EvidenceSufficiencyJudgeService;
import com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class EvidenceModelControllerTest {
    private static final List<EvidenceCandidate> CANDIDATES = List.of(
            new EvidenceCandidate("evidence-1", "RULEBOOK", "p. 4", "A rule excerpt"));

    @Test
    void evidenceEndpointsUseTheConfiguredGameMasterExecutionPath() {
        var wiring = java.util.Arrays.stream(AiGameMasterApiConfiguration.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("evidenceModelController"))
                .findFirst().orElseThrow();

        assertEquals(GmCompletionAdapter.class, wiring.getParameterTypes()[0]);
    }

    @Test
    void exposesRerankOnlyToInternalCallersAndMapsExhaustedInvalidOutputTo422() {
        var controller = controller("{\"orderedCandidateIds\":[\"unknown\"]}",
                "{\"orderedCandidateIds\":[\"unknown\"]}");

        var error = assertThrows(ResponseStatusException.class,
                () -> controller.rerank("secret", new EvidenceRerankRequest("door", "scene", CANDIDATES)));

        assertEquals(422, error.getStatusCode().value());
        assertEquals("EVIDENCE_MODEL_OUTPUT_INVALID", error.getReason());
    }

    @Test
    void rejectsMissingInternalTokenBeforeInvokingModel() {
        var controller = controller("{\"orderedCandidateIds\":[\"evidence-1\"]}");

        var error = assertThrows(ApiRequestGuard.ApiContractException.class,
                () -> controller.rerank(null, new EvidenceRerankRequest("door", "scene", CANDIDATES)));

        assertEquals(401, error.status());
    }

    private static EvidenceModelController controller(String... responses) {
        var model = new com.dndmaster.aigamemaster.application.evidence.EvidenceModelPort() {
            private int index;
            @Override public String complete(java.util.UUID soloPlayerId, String operationId, String instruction) { return responses[index++]; }
        };
        var mapper = new ObjectMapper();
        return new EvidenceModelController(new EvidenceRerankerService(model, mapper),
                new EvidenceSufficiencyJudgeService(model, mapper), new ApiRequestGuard("secret"));
    }
}
