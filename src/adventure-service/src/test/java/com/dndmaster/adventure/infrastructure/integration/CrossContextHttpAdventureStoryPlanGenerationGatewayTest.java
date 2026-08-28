package com.dndmaster.adventure.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanGenerationPort;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanCandidateValidationException;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanProjectionViolation;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanProjectionViolation.Repairability;
import com.dndmaster.adventure.application.storyplan.TacticalSceneRequest;
import com.dndmaster.adventure.domain.adventure.AdventureLength;
import com.dndmaster.adventure.domain.adventure.AdventurePlanConfiguration;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import com.dndmaster.adventure.domain.adventure.AdventureStageType;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.PublishedEvidenceProvenance;
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
    void preserves_rejected_full_projection_and_structured_field_violation_at_adapter_boundary() {
        server = new WireMockServer(0);
        server.start();
        server.stubFor(post(urlEqualTo("/internal/v1/gm/adventure-story-plan"))
                .willReturn(aResponse().withStatus(422).withHeader("Content-Type", "application/json").withBody("""
                        {"violations":[{"code":"INVALID_TRANSITION_CONDITION","stagePosition":2,
                        "fieldPath":"stages[1].transitionCondition","rejectedValue":"","citationContext":"citation-1",
                        "repairability":"REPAIRABLE","sanitizedMessage":"stage 2 transitionCondition is not usable"}],
                        "rejectedCandidate":{"stages":[{"position":1,"title":"Start"},{"position":2,"title":"Middle"}]}}
                        """)));
        var gateway = new CrossContextHttpAdventureStoryPlanGenerationGateway(HttpClient.newHttpClient(),
                URI.create(server.baseUrl() + "/"), Duration.ofSeconds(2), new ObjectMapper(), "test-internal-token");

        var failure = assertThrows(AdventureStoryPlanCandidateValidationException.class,
                () -> gateway.generate(new AdventureStoryPlanGenerationPort.Request(
                        "operation", 1, 1, new AdventurePlanConfiguration(1, AdventureLength.SHORT),
                        List.of(), List.of(), List.of(), List.of())));

        assertEquals(Repairability.REPAIRABLE, failure.structuredViolations().getFirst().repairability());
        assertEquals("stages[1].transitionCondition", failure.structuredViolations().getFirst().fieldPath());
        assertEquals("{\"stages\":[{\"position\":1,\"title\":\"Start\"},{\"position\":2,\"title\":\"Middle\"}]}",
                failure.rejectedCandidate());
    }

    @Test
    void sends_full_candidate_scope_and_all_authoritative_registries_to_repair_endpoint() throws Exception {
        server = new WireMockServer(0);
        server.start();
        server.stubFor(post(urlEqualTo("/internal/v1/gm/adventure-story-plan/repair"))
                .willReturn(aResponse().withStatus(422).withHeader("Content-Type", "application/json").withBody("""
                        {"violations":[{"code":"REJECTED","stagePosition":1,
                        "fieldPath":"stages[0].combatSkeleton.participants[0].name","rejectedValue":"",
                        "citationContext":"","repairability":"REPAIRABLE","sanitizedMessage":"rejected"}]}
                        """)));
        UUID documentId = UUID.randomUUID();
        var citation = new AdventureStoryPlanGenerationPort.SourceCitation(
                "STORYBOOK", documentId, 2, "page:1", "giant rat", .9).withCitationKey("rat-fact");
        var blocker = new AdventureStoryPlanProjectionViolation("REJECTED", 1,
                "stages[0].combatSkeleton.participants[0].name", "", "", Repairability.REPAIRABLE, "rejected");
        var gateway = new CrossContextHttpAdventureStoryPlanGenerationGateway(HttpClient.newHttpClient(),
                URI.create(server.baseUrl() + "/"), Duration.ofSeconds(2), new ObjectMapper(), "test-internal-token");

        assertThrows(AdventureStoryPlanCandidateValidationException.class, () -> gateway.repair(
                new AdventureStoryPlanGenerationPort.RepairRequest("operation", 3, 1,
                        new AdventurePlanConfiguration(1, AdventureLength.SHORT),
                        "{\"stages\":[{\"position\":1}]}", List.of(blocker),
                        List.of("storybook.pdf"), List.of("authoritative excerpt"), List.of(), List.of(citation))));

        server.verify(postRequestedFor(urlEqualTo("/internal/v1/gm/adventure-story-plan/repair"))
                .withRequestBody(matchingJsonPath("$.previousCandidate.stages[0].position", equalTo("1")))
                .withRequestBody(matchingJsonPath("$.repairScope.allowedPaths",
                        containing("stages[0].combatSkeleton.objective")))
                .withRequestBody(matchingJsonPath("$.citations[0].citationKey", equalTo("rat-fact")))
                .withRequestBody(matchingJsonPath("$.sourceDocuments[0]", equalTo("storybook.pdf")))
                .withRequestBody(matchingJsonPath("$.resolutionEvidence[0]", equalTo("authoritative excerpt"))));
    }

    @Test
    void repair_posts_the_failed_full_candidate_and_authoritative_registries_and_returns_a_full_candidate() {
        server = new WireMockServer(0);
        server.start();
        UUID documentId = UUID.randomUUID();
        String previous = projectionCandidate("Open");
        String repaired = projectionCandidate("Closed");
        server.stubFor(post(urlEqualTo("/internal/v1/gm/adventure-story-plan/repair"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(repaired)));
        var gateway = new CrossContextHttpAdventureStoryPlanGenerationGateway(HttpClient.newHttpClient(),
                URI.create(server.baseUrl() + "/"), Duration.ofSeconds(2), new ObjectMapper(), "test-internal-token");
        var citation = new AdventureStoryPlanGenerationPort.SourceCitation(
                "STORYBOOK", documentId, 1, "page:1", "authoritative", .9).withCitationKey("citation-1");
        var violation = new AdventureStoryPlanProjectionViolation("INVALID_TRANSITION_CONDITION", 1,
                "stages[0].transitionCondition", "Open", "citation-1", Repairability.REPAIRABLE,
                "transitionCondition is not usable");

        var candidate = gateway.repair(new AdventureStoryPlanGenerationPort.RepairRequest(
                "operation", 1, 1, new AdventurePlanConfiguration(1, AdventureLength.SHORT), previous,
                List.of(violation), List.of("storybook.pdf"), List.of("source excerpt"), List.of(), List.of(citation)));

        assertEquals(repaired, candidate.serializedCandidate());
        assertEquals("Closed", candidate.stages().getFirst().transitionCondition());
        server.verify(postRequestedFor(urlEqualTo("/internal/v1/gm/adventure-story-plan/repair"))
                .withRequestBody(matchingJsonPath("$.previousCandidate.stages[0].transitionCondition", equalTo("Open")))
                .withRequestBody(matchingJsonPath("$.violations[0].fieldPath", equalTo("stages[0].transitionCondition")))
                .withRequestBody(matchingJsonPath("$.citations[0].citationKey", equalTo("citation-1"))));
    }

    @Test
    void accepts_case_insensitive_stage_type_from_projection_agent() {
        server = new WireMockServer(0);
        server.start();
        server.stubFor(post(urlEqualTo("/internal/v1/gm/adventure-story-plan"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"stages":[
                          {"position":1,"title":"Start","goal":"Begin","conflict":"Choice","transitionCondition":"Continue","npcOrClues":[],"endingIds":["ending-1"],"stageType":"마을","location":"Start","enemies":[],"rewards":[],"branchIds":[],"branchTargets":{},"evidence":[]},
                          {"position":2,"title":"Middle","goal":"Advance","conflict":"Choice","transitionCondition":"Continue","npcOrClues":[],"endingIds":["ending-1"],"stageType":"던전","location":"Middle","enemies":[],"rewards":[],"branchIds":[],"branchTargets":{},"evidence":[]},
                          {"position":3,"title":"Finish","goal":"End","conflict":"Choice","transitionCondition":"Finish","npcOrClues":[],"endingIds":["ending-1"],"stageType":"이벤트","location":"Finish","enemies":[],"rewards":[],"branchIds":[],"branchTargets":{},"evidence":[]}
                        ]}
                        """)));
        var gateway = new CrossContextHttpAdventureStoryPlanGenerationGateway(HttpClient.newHttpClient(),
                URI.create(server.baseUrl() + "/"), Duration.ofSeconds(2), new ObjectMapper(), "test-internal-token");

        var candidate = gateway.generate(new AdventureStoryPlanGenerationPort.Request(
                "operation", 1, 1, new AdventurePlanConfiguration(1, AdventureLength.SHORT),
                List.of(), List.of(), List.of(), List.of()));

        assertEquals(List.of(AdventureStageType.TOWN, AdventureStageType.DUNGEON, AdventureStageType.EVENT),
                candidate.stages().stream().map(AdventureStoryPlanStage::stageType).toList());
    }

    @Test
    void rejects_localized_dungeon_without_supplied_map() {
        server = new WireMockServer(0);
        server.start();
        UUID mapId = UUID.randomUUID();
        server.stubFor(post(urlEqualTo("/internal/v1/gm/adventure-story-plan"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"stages":[
                          {"position":1,"title":"Start","goal":"Begin","conflict":"Choice","transitionCondition":"Continue","npcOrClues":[],"endingIds":["ending-1"],"stageType":"마을","location":"Start","enemies":[],"rewards":[],"branchIds":[],"branchTargets":{},"evidence":[]},
                          {"position":2,"title":"Middle","goal":"Advance","conflict":"Choice","transitionCondition":"Continue","npcOrClues":[],"endingIds":["ending-1"],"stageType":"던전","location":"Middle","mapUsage":"OPTIONAL","enemies":[],"rewards":[],"branchIds":[],"branchTargets":{},"evidence":[]},
                          {"position":3,"title":"Finish","goal":"End","conflict":"Choice","transitionCondition":"Finish","npcOrClues":[],"endingIds":["ending-1"],"stageType":"이벤트","location":"Finish","enemies":[],"rewards":[],"branchIds":[],"branchTargets":{},"evidence":[]}
                        ]}
                        """)));
        var gateway = new CrossContextHttpAdventureStoryPlanGenerationGateway(HttpClient.newHttpClient(),
                URI.create(server.baseUrl() + "/"), Duration.ofSeconds(2), new ObjectMapper(), "test-internal-token");

        var failure = assertThrows(AdventureStoryPlanCandidateValidationException.class,
                () -> gateway.generate(new AdventureStoryPlanGenerationPort.Request(
                        "operation", 1, 1, new AdventurePlanConfiguration(1, AdventureLength.SHORT),
                        List.of(), List.of(), List.of(new AdventureStoryPlanGenerationPort.MapContext(
                                mapId, "brewery", "page 1 image 1", "page 1 image 1", .9, "SAFE")), List.of())));

        assertEquals(List.of("map-backed bundle requires every dungeon stage to reference a map definition"), failure.violations());
    }

    @Test
    void canonicalizes_provider_quote_formatting_to_supplied_source_evidence() {
        server = new WireMockServer(0);
        server.start();
        UUID documentId = UUID.randomUUID();
        server.stubFor(post(urlEqualTo("/internal/v1/gm/adventure-story-plan"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"stages":[
                          {"position":1,"title":"Start","goal":"Begin","conflict":"Choice","transitionCondition":"Continue","npcOrClues":[],"endingIds":["ending-1"],"stageType":"EVENT","location":"Start","evidence":[{"citationKey":"citation-1"}],"enemies":[],"rewards":[],"branchIds":[],"branchTargets":{}},
                          {"position":2,"title":"Middle","goal":"Advance","conflict":"Choice","transitionCondition":"Continue","npcOrClues":[],"endingIds":["ending-1"],"stageType":"EVENT","location":"Middle","evidence":[{"citationKey":"citation-1"}],"enemies":[],"rewards":[],"branchIds":[],"branchTargets":{}},
                          {"position":3,"title":"Finish","goal":"End","conflict":"Choice","transitionCondition":"Finish","npcOrClues":[],"endingIds":["ending-1"],"stageType":"EVENT","location":"Finish","evidence":[{"citationKey":"citation-1"}],"enemies":[],"rewards":[],"branchIds":[],"branchTargets":{}}
                        ]}
                        """.formatted(documentId, documentId, documentId))));
        var gateway = new CrossContextHttpAdventureStoryPlanGenerationGateway(HttpClient.newHttpClient(),
                URI.create(server.baseUrl() + "/"), Duration.ofSeconds(2), new ObjectMapper(), "test-internal-token");
        var request = new AdventureStoryPlanGenerationPort.Request(
                "operation", 1, 1, new AdventurePlanConfiguration(1, AdventureLength.SHORT), List.of(), List.of(), List.of(),
                List.of(new AdventureStoryPlanGenerationPort.SourceCitation(
                        "STORYBOOK", documentId, 1, "page:1", "authoritative quote", .9)));

        var candidate = gateway.generate(request);

        assertEquals("authoritative quote", candidate.stages().getFirst().evidence().getFirst().quote());
    }

    @Test
    void resolves_request_local_citation_key_to_server_owned_citation_and_ignores_provider_fields() {
        server = new WireMockServer(0);
        server.start();
        UUID documentId = UUID.randomUUID();
        var citation = new AdventureStoryPlanGenerationPort.SourceCitation(
                "STORYBOOK", documentId, 7, "page:4:block:2", "server-owned quote", .93);
        server.stubFor(post(urlEqualTo("/internal/v1/gm/adventure-story-plan"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"stages":[
                          {"position":1,"title":"Start","goal":"Begin","conflict":"Choice","transitionCondition":"Continue","npcOrClues":[],"endingIds":["ending-1"],"stageType":"EVENT","location":"Start","evidence":[{"citationKey":"citation-1","documentType":"RULEBOOK","documentId":"00000000-0000-0000-0000-000000000001","extractionVersion":999,"locator":"tampered","quote":"tampered","confidence":0.01}],"enemies":[],"rewards":[],"branchIds":[],"branchTargets":{}},
                          {"position":2,"title":"Middle","goal":"Advance","conflict":"Choice","transitionCondition":"Continue","npcOrClues":[],"endingIds":["ending-1"],"stageType":"EVENT","location":"Middle","evidence":[{"citationKey":"citation-1","documentType":"RULEBOOK","documentId":"00000000-0000-0000-0000-000000000001","extractionVersion":999,"locator":"tampered","quote":"tampered","confidence":0.01}],"enemies":[],"rewards":[],"branchIds":[],"branchTargets":{}},
                          {"position":3,"title":"Finish","goal":"End","conflict":"Choice","transitionCondition":"Finish","npcOrClues":[],"endingIds":["ending-1"],"stageType":"EVENT","location":"Finish","evidence":[{"citationKey":"citation-1","documentType":"RULEBOOK","documentId":"00000000-0000-0000-0000-000000000001","extractionVersion":999,"locator":"tampered","quote":"tampered","confidence":0.01}],"enemies":[],"rewards":[],"branchIds":[],"branchTargets":{}}
                        ]}
                        """)));
        var gateway = new CrossContextHttpAdventureStoryPlanGenerationGateway(HttpClient.newHttpClient(),
                URI.create(server.baseUrl() + "/"), Duration.ofSeconds(2), new ObjectMapper(), "test-internal-token");

        var candidate = gateway.generate(new AdventureStoryPlanGenerationPort.Request(
                "operation", 1, 1, new AdventurePlanConfiguration(1, AdventureLength.SHORT), List.of(), List.of(), List.of(), List.of(citation)));

        var evidence = candidate.stages().getFirst().evidence().getFirst();
        assertEquals(citation.documentType(), evidence.documentType());
        assertEquals(citation.documentId(), evidence.documentId());
        assertEquals(citation.extractionVersion(), evidence.extractionVersion());
        assertEquals(citation.locator(), evidence.locator());
        assertEquals(citation.quote(), evidence.quote());
        assertEquals(citation.confidence(), evidence.confidence());
        assertEquals(citation.provenance(), evidence.provenance());
        server.verify(postRequestedFor(urlEqualTo("/internal/v1/gm/adventure-story-plan"))
                .withRequestBody(matchingJsonPath("$.citations[0].citationKey", equalTo("citation-1"))));
    }

    @Test
    void preserves_caller_key_across_gateway_boundary_and_resolves_canonical_provenance() {
        server = new WireMockServer(0);
        server.start();
        UUID documentId = UUID.randomUUID();
        var provenance = new PublishedEvidenceProvenance(
                new KnowledgeDocumentId(documentId), 7, 4, List.of("Chapter 1"), List.of(1.0, 2.0, 3.0, 4.0),
                "cell-A", "page:4:block:2");
        var citation = new AdventureStoryPlanGenerationPort.SourceCitation(
                "STORYBOOK", documentId, 7, "page:4:block:2", "server-owned quote", .93, provenance)
                .withCitationKey("stable-story");
        server.stubFor(post(urlEqualTo("/internal/v1/gm/adventure-story-plan"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"stages":[
                          {"position":1,"title":"Start","goal":"Begin","conflict":"Choice","transitionCondition":"Continue","npcOrClues":[],"endingIds":["ending-1"],"stageType":"EVENT","location":"Start","evidence":[{"citationKey":"stable-story","locator":"tampered","quote":"tampered","confidence":0.01}],"enemies":[],"rewards":[],"branchIds":[],"branchTargets":{}},
                          {"position":2,"title":"Middle","goal":"Advance","conflict":"Choice","transitionCondition":"Continue","npcOrClues":[],"endingIds":["ending-1"],"stageType":"EVENT","location":"Middle","evidence":[{"citationKey":"stable-story"}],"enemies":[],"rewards":[],"branchIds":[],"branchTargets":{}},
                          {"position":3,"title":"Finish","goal":"End","conflict":"Choice","transitionCondition":"Finish","npcOrClues":[],"endingIds":["ending-1"],"stageType":"EVENT","location":"Finish","evidence":[{"citationKey":"stable-story"}],"enemies":[],"rewards":[],"branchIds":[],"branchTargets":{}}
                        ]}
                        """)));
        var gateway = new CrossContextHttpAdventureStoryPlanGenerationGateway(HttpClient.newHttpClient(),
                URI.create(server.baseUrl() + "/"), Duration.ofSeconds(2), new ObjectMapper(), "test-internal-token");

        var candidate = gateway.generate(new AdventureStoryPlanGenerationPort.Request(
                "operation", 1, 1, new AdventurePlanConfiguration(1, AdventureLength.SHORT), List.of(), List.of(), List.of(), List.of(citation)));

        var evidence = candidate.stages().getFirst().evidence().getFirst();
        assertEquals(citation.documentType(), evidence.documentType());
        assertEquals(citation.documentId(), evidence.documentId());
        assertEquals(citation.extractionVersion(), evidence.extractionVersion());
        assertEquals(citation.locator(), evidence.locator());
        assertEquals(citation.quote(), evidence.quote());
        assertEquals(citation.confidence(), evidence.confidence());
        assertEquals(citation.provenance(), evidence.provenance());
        server.verify(postRequestedFor(urlEqualTo("/internal/v1/gm/adventure-story-plan"))
                .withRequestBody(matchingJsonPath("$.citations[0].citationKey", equalTo("stable-story"))));
    }

    @Test
    void preserves_caller_citation_keys_and_assigns_deterministic_unused_keys() {
        UUID firstDocumentId = UUID.randomUUID();
        UUID secondDocumentId = UUID.randomUUID();
        UUID thirdDocumentId = UUID.randomUUID();
        UUID fourthDocumentId = UUID.randomUUID();
        var request = new AdventureStoryPlanGenerationPort.Request(
                "operation", 1, 1, new AdventurePlanConfiguration(1, AdventureLength.SHORT), List.of(), List.of(), List.of(),
                List.of(
                        new AdventureStoryPlanGenerationPort.SourceCitation("STORYBOOK", firstDocumentId, 1, "page:1", "first", .9)
                                .withCitationKey("stable-story"),
                        new AdventureStoryPlanGenerationPort.SourceCitation("STORYBOOK", secondDocumentId, 1, "page:2", "second", .9),
                        new AdventureStoryPlanGenerationPort.SourceCitation("RULEBOOK", thirdDocumentId, 1, "page:3", "third", .9)
                                .withCitationKey("citation-2"),
                        new AdventureStoryPlanGenerationPort.SourceCitation("RULEBOOK", fourthDocumentId, 1, "page:4", "fourth", .9)));

        var keyed = request.withCitationKeys();

        assertEquals(List.of("stable-story", "citation-1", "citation-2", "citation-3"),
                keyed.citations().stream().map(AdventureStoryPlanGenerationPort.SourceCitation::citationKey).toList());
    }

    @Test
    void reserves_later_caller_key_before_assigning_generated_key_to_blank_citation() {
        var request = new AdventureStoryPlanGenerationPort.Request(
                "operation", 1, 1, new AdventurePlanConfiguration(1, AdventureLength.SHORT), List.of(), List.of(), List.of(),
                List.of(
                        new AdventureStoryPlanGenerationPort.SourceCitation("STORYBOOK", UUID.randomUUID(), 1, "page:1", "first", .9),
                        new AdventureStoryPlanGenerationPort.SourceCitation("STORYBOOK", UUID.randomUUID(), 1, "page:2", "second", .9)
                                .withCitationKey("citation-1"),
                        new AdventureStoryPlanGenerationPort.SourceCitation("RULEBOOK", UUID.randomUUID(), 1, "page:3", "third", .9)));

        var keyed = request.withCitationKeys();

        assertEquals(List.of("citation-2", "citation-1", "citation-3"),
                keyed.citations().stream().map(AdventureStoryPlanGenerationPort.SourceCitation::citationKey).toList());
    }

    @Test
    void rejects_duplicate_caller_citation_keys_before_sending_or_resolving() {
        server = new WireMockServer(0);
        server.start();
        var duplicateKey = "stable-story";
        var request = new AdventureStoryPlanGenerationPort.Request(
                "operation", 1, 1, new AdventurePlanConfiguration(1, AdventureLength.SHORT), List.of(), List.of(), List.of(),
                List.of(
                        new AdventureStoryPlanGenerationPort.SourceCitation("STORYBOOK", UUID.randomUUID(), 1, "page:1", "first", .9)
                                .withCitationKey(duplicateKey),
                        new AdventureStoryPlanGenerationPort.SourceCitation("STORYBOOK", UUID.randomUUID(), 1, "page:2", "second", .9)
                                .withCitationKey(duplicateKey)));

        var gateway = new CrossContextHttpAdventureStoryPlanGenerationGateway(HttpClient.newHttpClient(),
                URI.create(server.baseUrl() + "/"), Duration.ofSeconds(2), new ObjectMapper(), "test-internal-token");

        var failure = assertThrows(IllegalArgumentException.class, () -> gateway.generate(request));

        assertEquals("duplicate citation key: " + duplicateKey, failure.getMessage());
        server.verify(exactly(0), postRequestedFor(urlEqualTo("/internal/v1/gm/adventure-story-plan")));
    }

    @Test
    void rejects_unknown_request_local_citation_key_clearly() {
        server = new WireMockServer(0);
        server.start();
        server.stubFor(post(urlEqualTo("/internal/v1/gm/adventure-story-plan"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"stages":[
                          {"position":1,"title":"Start","goal":"Begin","conflict":"Choice","transitionCondition":"Continue","npcOrClues":[],"endingIds":["ending-1"],"stageType":"EVENT","location":"Start","evidence":[{"citationKey":"citation-999"}],"enemies":[],"rewards":[],"branchIds":[],"branchTargets":{}},
                          {"position":2,"title":"Middle","goal":"Advance","conflict":"Choice","transitionCondition":"Continue","npcOrClues":[],"endingIds":["ending-1"],"stageType":"EVENT","location":"Middle","evidence":[{"citationKey":"citation-1"}],"enemies":[],"rewards":[],"branchIds":[],"branchTargets":{}},
                          {"position":3,"title":"Finish","goal":"End","conflict":"Choice","transitionCondition":"Finish","npcOrClues":[],"endingIds":["ending-1"],"stageType":"EVENT","location":"Finish","evidence":[{"citationKey":"citation-1"}],"enemies":[],"rewards":[],"branchIds":[],"branchTargets":{}}
                        ]}
                        """)));
        var gateway = new CrossContextHttpAdventureStoryPlanGenerationGateway(HttpClient.newHttpClient(),
                URI.create(server.baseUrl() + "/"), Duration.ofSeconds(2), new ObjectMapper(), "test-internal-token");
        var citation = new AdventureStoryPlanGenerationPort.SourceCitation(
                "STORYBOOK", UUID.randomUUID(), 1, "page:1", "quote", .8);

        var failure = assertThrows(AdventureStoryPlanCandidateValidationException.class,
                () -> gateway.generate(new AdventureStoryPlanGenerationPort.Request(
                        "operation", 1, 1, new AdventurePlanConfiguration(1, AdventureLength.SHORT),
                        List.of(), List.of(), List.of(), List.of(citation))));

        assertEquals(List.of("AI returned an unknown citation key"), failure.violations());
        assertEquals("citation-999", failure.structuredViolations().getFirst().rejectedValue());
    }

    @Test
    void normalizesMissingEndingIdsProjectionAsTypedCandidateValidation() {
        server = new WireMockServer(0);
        server.start();
        server.stubFor(post(urlEqualTo("/internal/v1/gm/adventure-story-plan"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"stages":[{"position":1,"title":"Start","goal":"Begin","conflict":"Choice",
                        "transitionCondition":"Continue","npcOrClues":[],"stageType":"EVENT","location":"Start",
                        "enemies":[],"rewards":[],"branchIds":[],"branchTargets":{},"evidence":[]}]}
                        """)));
        var gateway = new CrossContextHttpAdventureStoryPlanGenerationGateway(HttpClient.newHttpClient(),
                URI.create(server.baseUrl() + "/"), Duration.ofSeconds(2), new ObjectMapper(), "test-internal-token");

        var failure = assertThrows(AdventureStoryPlanCandidateValidationException.class,
                () -> gateway.generate(new AdventureStoryPlanGenerationPort.Request(
                        "operation", 1, 1, new AdventurePlanConfiguration(1, AdventureLength.SHORT),
                        List.of(), List.of(), List.of(), List.of())));

        assertEquals(List.of("endingIds must be explicit"), failure.violations());
    }

    @Test
    void normalizesEmptyEndingIdsProjectionAsTypedCandidateValidation() {
        server = new WireMockServer(0);
        server.start();
        server.stubFor(post(urlEqualTo("/internal/v1/gm/adventure-story-plan"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"stages":[{"position":1,"title":"Start","goal":"Begin","conflict":"Choice",
                        "transitionCondition":"Continue","npcOrClues":[],"endingIds":[],"stageType":"EVENT","location":"Start",
                        "enemies":[],"rewards":[],"branchIds":[],"branchTargets":{},"evidence":[]}]}
                        """)));
        var gateway = new CrossContextHttpAdventureStoryPlanGenerationGateway(HttpClient.newHttpClient(),
                URI.create(server.baseUrl() + "/"), Duration.ofSeconds(2), new ObjectMapper(), "test-internal-token");

        var failure = assertThrows(AdventureStoryPlanCandidateValidationException.class,
                () -> gateway.generate(new AdventureStoryPlanGenerationPort.Request(
                        "operation", 1, 1, new AdventurePlanConfiguration(1, AdventureLength.SHORT),
                        List.of(), List.of(), List.of(), List.of())));

        assertEquals(List.of("endingIds must not be empty"), failure.violations());
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
    void preservesAllRemoteStoryPlanVerificationViolations() {
        server = new WireMockServer(0);
        server.start();
        server.stubFor(post(urlEqualTo("/internal/v1/gm/adventure-story-plan"))
                .willReturn(aResponse().withStatus(422).withHeader("Content-Type", "application/json")
                        .withBody("{\"detail\":\"story plan rejected\",\"violations\":[\"필수 목표가 없습니다\",\"필요한 판정 결과가 연결되지 않았습니다\"]}")));
        var gateway = new CrossContextHttpAdventureStoryPlanGenerationGateway(HttpClient.newHttpClient(),
                URI.create(server.baseUrl() + "/"), Duration.ofSeconds(2), new ObjectMapper(), "test-internal-token");
        var request = new AdventureStoryPlanGenerationPort.Request(
                "operation", 1, 1, new AdventurePlanConfiguration(1, AdventureLength.SHORT),
                List.of(), List.of(), List.of(), List.of());

        var failure = assertThrows(AdventureStoryPlanCandidateValidationException.class,
                () -> gateway.generate(request));

        assertEquals(List.of("필수 목표가 없습니다", "필요한 판정 결과가 연결되지 않았습니다"), failure.violations());
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

    private static String projectionCandidate(String transition) {
        return """
                {"stages":[
                  {"position":1,"title":"Start","goal":"Begin","conflict":"Choice","transitionCondition":"%s","npcOrClues":[],"endingIds":["ending-1"],"stageType":"EVENT","location":"Start","enemies":[],"rewards":[],"branchIds":[],"branchTargets":{},"evidence":[{"citationKey":"citation-1"}]},
                  {"position":2,"title":"Middle","goal":"Advance","conflict":"Choice","transitionCondition":"Continue","npcOrClues":[],"endingIds":["ending-1"],"stageType":"EVENT","location":"Middle","enemies":[],"rewards":[],"branchIds":[],"branchTargets":{},"evidence":[{"citationKey":"citation-1"}]},
                  {"position":3,"title":"Finish","goal":"End","conflict":"Choice","transitionCondition":"Finish","npcOrClues":[],"endingIds":["ending-1"],"stageType":"EVENT","location":"Finish","enemies":[],"rewards":[],"branchIds":[],"branchTargets":{},"evidence":[{"citationKey":"citation-1"}]}
                ]}
                """.formatted(transition).replaceAll("\\s+", "");
    }
}
