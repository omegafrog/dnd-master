package com.dndmaster.adventure.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanGenerationPort;
import com.dndmaster.adventure.application.storyplan.TacticalSceneRequest;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import com.dndmaster.adventure.domain.adventure.AdventureStageType;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CrossContextHttpAdventureStoryPlanGenerationGatewayTest {
    private WireMockServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop();
    }

    @Test
    void parsesTypedTacticalCandidateAndKeepsOnlySuppliedCitationGrounding() {
        server = new WireMockServer(0);
        server.start();
        UUID documentId = UUID.randomUUID();
        server.stubFor(post(urlEqualTo("/internal/v1/gm/tactical-scene-plan"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"stagePosition":1,"scene":{"schemaVersion":1,"status":"READY",
                        "boundary":{"minimum":{"x":0,"y":0},"maximum":{"x":1,"y":1},"forbiddenCoordinates":[]},
                        "players":[{"id":"party","kind":"PLAYER","coordinate":{"x":0.1,"y":0.1},"grounding":{"type":"SOURCE_CITATION","citation":"%s:page:1","rationale":""}}],
                        "allies":[],"npcs":[],"enemies":[],"bosses":[],"interactiveObjects":[],"environments":[],
                        "initialFog":{"hiddenRegions":[],"grounding":{"type":"SOURCE_CITATION","citation":"%s:page:1","rationale":""}},
                        "triggers":[],"outcomes":[],"transitionIds":[]},
                        "citations":[{"documentType":"STORYBOOK","documentId":"%s","extractionVersion":1,"locator":"page:1","quote":"cellar entrance","confidence":0.8}]}
                        """.formatted(documentId, documentId, documentId))));

        var gateway = new CrossContextHttpAdventureStoryPlanGenerationGateway(HttpClient.newHttpClient(),
                URI.create(server.baseUrl() + "/"), Duration.ofSeconds(2), new ObjectMapper());
        var request = request(documentId);

        var candidate = gateway.generateTacticalScene(request);

        assertEquals(1, candidate.stagePosition());
        assertEquals("%s:page:1".formatted(documentId), candidate.scene().players().getFirst().grounding().citation());
        assertEquals(1, candidate.citations().size());
        server.verify(exactly(1), postRequestedFor(urlEqualTo("/internal/v1/gm/tactical-scene-plan")));
    }

    private static TacticalSceneRequest request(UUID documentId) {
        var stage = new AdventureStoryPlanStage(1, "Cellar", "Clear", "Rats", "Leave", List.of(), List.of("ending"),
                List.of(), AdventureStageType.DUNGEON, "Cellar", UUID.randomUUID(), "brewery", "page:1", List.of(), "", "Leave", "", List.of(),
                List.of("ending"), List.of(), null, List.of(), "SAFE", .9);
        var map = new AdventureStoryPlanGenerationPort.MapContext(stage.mapDefinitionId(), "brewery", "page:1", "page:1", .9, "SAFE", List.of());
        var citation = new AdventureStoryPlanGenerationPort.SourceCitation("STORYBOOK", documentId, 1, "page:1", "cellar entrance", .8);
        return new TacticalSceneRequest(stage, map, List.of(citation), List.of());
    }
}
