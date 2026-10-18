package com.dndmaster.aigamemaster.api;

import org.junit.jupiter.api.Test;

import com.dndmaster.aigamemaster.application.intent.IntentClassificationModelPort;
import com.dndmaster.aigamemaster.application.ports.AdjudicationModelPort;
import com.dndmaster.aigamemaster.application.ports.MapModelPort;
import com.dndmaster.aigamemaster.application.ports.SpatialFeaturePlacementModelPort;
import com.dndmaster.aigamemaster.application.rule.EvidenceStatus;
import com.dndmaster.aigamemaster.application.rule.RuleAnswerRequest;
import com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionAdapter;
import com.dndmaster.aigamemaster.infrastructure.ai.GmPrompt;
import com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiGameMasterControllerTest {

    @Test
    void proposesCompanionWithoutSeparateModelEndpoint() {
        AiGameMasterController controller = new AiGameMasterController(null, null, null, null, null, null, null);

        AiGameMasterController.CompanionCandidateResponse response = controller.proposeCompanion(
                new AiGameMasterController.CompanionCandidateRequest(UUID.randomUUID()));

        assertEquals("린", response.name());
        assertEquals("엘프", response.race());
        assertEquals("클레릭", response.characterClass());
    }

    @Test
    void passes_the_server_confirmed_solo_player_id_from_every_legacy_model_input() {
        UUID soloPlayerId = UUID.randomUUID();
        var seen = new CopyOnWriteArrayList<UUID>();
        GmCompletionAdapter adapter = new GmCompletionAdapter() {
            @Override public <T> T complete(String operationId, String prompt, StructuredResponseParser<T> parser) {
                return parser.parse("{}");
            }
            @Override public <T> T complete(UUID owner, String operationId, String prompt, StructuredResponseParser<T> parser) {
                seen.add(owner);
                String response = operationId.startsWith("map-")
                        ? "{\"width\":1,\"height\":1,\"boundaries\":[],\"obstacles\":[],\"doors\":[]}"
                        : operationId.startsWith("spatial-feature-placement-") ? "{\"candidates\":[]}" : "RULE";
                return parser.parse(response);
            }
            @Override public <T> T complete(UUID owner, String operationId, GmPrompt prompt, StructuredResponseParser<T> parser) {
                return complete(owner, operationId, prompt.text(), parser);
            }
        };
        var configuration = new AiGameMasterApiConfiguration();

        configuration.ruleAnswerModelPort(adapter).compose(new RuleAnswerRequest(soloPlayerId, UUID.randomUUID(), "문", EvidenceStatus.INSUFFICIENT, java.util.List.of()));
        configuration.adjudicationModelPort(adapter).adjudicate(new AdjudicationModelPort.AdjudicationInput(soloPlayerId, "연다", "문", "rules"));
        configuration.mapModelPort(adapter, new ObjectMapper()).generate(new MapModelPort.MapInput(soloPlayerId, "장면", "문", ""));
        configuration.spatialFeaturePlacementModelPort(adapter, new ObjectMapper()).propose(
                new SpatialFeaturePlacementModelPort.PlacementInput(soloPlayerId, "4", 1, java.util.List.of(),
                        5, 4, java.util.List.of(), java.util.List.of()));
        configuration.intentClassificationModelPort(adapter).classify(new IntentClassificationModelPort.IntentClassificationInput(soloPlayerId, "문"));

        assertEquals(java.util.List.of(soloPlayerId, soloPlayerId, soloPlayerId, soloPlayerId, soloPlayerId), seen);
    }

    @Test
    void forwards_the_connected_player_and_source_grid_to_spatial_placement() {
        UUID soloPlayerId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();
        var seen = new java.util.concurrent.atomic.AtomicReference<SpatialFeaturePlacementModelPort.PlacementInput>();
        SpatialFeaturePlacementModelPort port = input -> {
            seen.set(input);
            return new SpatialFeaturePlacementModelPort.PlacementOutput(java.util.List.of());
        };
        var controller = new AiGameMasterController(null, null, null, null, null, port, null);
        controller.proposeSpatialFeatures(new AiGameMasterController.SpatialFeaturePlacementRequest(
                soloPlayerId, "4", 1, java.util.List.of(), 5, 4, java.util.List.of("1,1"),
                java.util.List.of(new AiGameMasterController.SpatialFeaturePlacementRequest.Requirement(
                        featureId, "TRAP", true, java.util.List.of("source"), java.util.List.of("4,3")))));
        assertEquals(soloPlayerId, seen.get().soloPlayerId());
        assertEquals(5, seen.get().gridWidth());
        assertEquals(4, seen.get().gridHeight());
        assertEquals(java.util.List.of("1,1"), seen.get().obstacles());
    }
}
