package com.dndmaster.adventure.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanGenerationPort;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanCandidateValidationException;
import com.dndmaster.adventure.application.storyplan.TacticalSceneRequest;
import com.dndmaster.adventure.domain.adventure.AdventureLength;
import com.dndmaster.adventure.domain.adventure.AdventurePlanConfiguration;
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
                        "players":[{"id":"party","kind":"PLAYER","coordinate":{"x":0.1,"y":0.1},"grounding":{"type":"SOURCE_CITATION","citation":"STORYBOOK:%s:1:page:1","rationale":""}}],
                        "allies":[],"npcs":[],"enemies":[],"bosses":[],"interactiveObjects":[],"environments":[],
                        "initialFog":{"hiddenRegions":[],"grounding":{"type":"SOURCE_CITATION","citation":"STORYBOOK:%s:1:page:1","rationale":""}},
                        "triggers":[],"outcomes":[],"transitionIds":[]},
                        "citations":[{"documentType":"STORYBOOK","documentId":"%s","extractionVersion":1,"locator":"page:1","quote":"cellar entrance","confidence":0.8}]}
                        """.formatted(documentId, documentId, documentId))));

        var gateway = new CrossContextHttpAdventureStoryPlanGenerationGateway(HttpClient.newHttpClient(),
                URI.create(server.baseUrl() + "/"), Duration.ofSeconds(2), new ObjectMapper(), "test-internal-token");
        var request = request(documentId);

        var candidate = gateway.generateTacticalScene(request);

        assertEquals(1, candidate.stagePosition());
        assertEquals("STORYBOOK:%s:1:page:1".formatted(documentId), candidate.scene().players().getFirst().grounding().citation());
        assertEquals(1, candidate.citations().size());
        server.verify(exactly(1), postRequestedFor(urlEqualTo("/internal/v1/gm/tactical-scene-plan")));
        server.verify(postRequestedFor(urlEqualTo("/internal/v1/gm/tactical-scene-plan"))
                .withHeader("X-Internal-Token", equalTo("test-internal-token")));
    }

    @Test
    void rejectsMissingInternalTokenAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new CrossContextHttpAdventureStoryPlanGenerationGateway(
                HttpClient.newHttpClient(), URI.create("http://localhost/"), Duration.ofSeconds(1), new ObjectMapper(), " "));
    }

    @Test
    void normalizesInvalidOutlineOutputAsTypedCandidateValidation() {
        server = new WireMockServer(0);
        server.start();
        server.stubFor(post(urlEqualTo("/internal/v1/gm/adventure-story-plan"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"stages\":[]}")));
        var gateway = new CrossContextHttpAdventureStoryPlanGenerationGateway(HttpClient.newHttpClient(),
                URI.create(server.baseUrl() + "/"), Duration.ofSeconds(2), new ObjectMapper(), "test-internal-token");
        var request = new AdventureStoryPlanGenerationPort.Request(
                "operation", 1, 1, new AdventurePlanConfiguration(1, AdventureLength.SHORT),
                List.of(), List.of(), List.of(), List.of());

        var failure = assertThrows(AdventureStoryPlanCandidateValidationException.class,
                () -> gateway.generate(request));

        assertEquals(List.of("AI returned an invalid stage count for adventure length"), failure.violations());
    }

    @Test
    void normalizesRemoteMalformedTacticalCandidateAsTypedValidationButNotProviderFailure() {
        server = new WireMockServer(0);
        server.start();
        server.stubFor(post(urlEqualTo("/internal/v1/gm/tactical-scene-plan"))
                .willReturn(aResponse().withStatus(422).withHeader("Content-Type", "application/json")
                        .withBody("{\"detail\":\"tactical candidate fields missing\"}")));
        var gateway = new CrossContextHttpAdventureStoryPlanGenerationGateway(HttpClient.newHttpClient(),
                URI.create(server.baseUrl() + "/"), Duration.ofSeconds(2), new ObjectMapper(), "test-internal-token");

        var failure = assertThrows(AdventureStoryPlanCandidateValidationException.class,
                () -> gateway.generateTacticalScene(request(UUID.randomUUID())));

        assertEquals(List.of("tactical candidate fields missing"), failure.violations());
    }

    @Test
    void keepsRemoteTacticalProviderFailureOperational() {
        server = new WireMockServer(0);
        server.start();
        server.stubFor(post(urlEqualTo("/internal/v1/gm/tactical-scene-plan"))
                .willReturn(aResponse().withStatus(503)));
        var gateway = new CrossContextHttpAdventureStoryPlanGenerationGateway(HttpClient.newHttpClient(),
                URI.create(server.baseUrl() + "/"), Duration.ofSeconds(2), new ObjectMapper(), "test-internal-token");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> gateway.generateTacticalScene(request(UUID.randomUUID())));

        assertEquals("tactical scene AI failed: 503", failure.getMessage());
    }

    @Test
    void rejectsOmittedRequiredOutlineCollectionsInsteadOfInventingEmptyLists() {
        server = new WireMockServer(0);
        server.start();
        server.stubFor(post(urlEqualTo("/internal/v1/gm/adventure-story-plan"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"stages":[
                          {"position":1,"title":"Start","goal":"Begin","conflict":"Choice","transitionCondition":"Continue","npcOrClues":[],"endingIds":["ending-1"],"stageType":"EVENT","location":"Start","rewards":[],"branchIds":["ending-1"],"branchTargets":{},"evidence":[]},
                          {"position":2,"title":"Middle","goal":"Advance","conflict":"Choice","transitionCondition":"Continue","npcOrClues":[],"endingIds":["ending-1"],"stageType":"EVENT","location":"Middle","enemies":[],"rewards":[],"branchIds":["ending-1"],"branchTargets":{},"evidence":[]},
                          {"position":3,"title":"Finish","goal":"End","conflict":"Choice","transitionCondition":"Finish","npcOrClues":[],"endingIds":["ending-1"],"stageType":"EVENT","location":"Finish","enemies":[],"rewards":[],"branchIds":["ending-1"],"branchTargets":{},"evidence":[]}
                        ]}
                        """)));
        var gateway = new CrossContextHttpAdventureStoryPlanGenerationGateway(HttpClient.newHttpClient(),
                URI.create(server.baseUrl() + "/"), Duration.ofSeconds(2), new ObjectMapper(), "test-internal-token");
        var request = new AdventureStoryPlanGenerationPort.Request(
                "operation", 1, 1, new AdventurePlanConfiguration(1, AdventureLength.SHORT),
                List.of(), List.of(), List.of(), List.of());

        var failure = assertThrows(AdventureStoryPlanCandidateValidationException.class,
                () -> gateway.generate(request));

        assertEquals(List.of("enemies must be explicit"), failure.violations());
    }

    @Test
    void extractsMapDefinitionUuidWhenAgentAddsTextAroundTheSuppliedId() {
        UUID mapId = UUID.randomUUID();

        assertEquals(mapId, CrossContextHttpAdventureStoryPlanGenerationGateway.parseMapDefinitionId(
                "Map definition ID: " + mapId + " (use the supplied map)"));
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
