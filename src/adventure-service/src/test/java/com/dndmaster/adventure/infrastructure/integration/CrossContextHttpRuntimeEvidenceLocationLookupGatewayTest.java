package com.dndmaster.adventure.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.runtime.RuntimeEvidence;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceType;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.evidence.EvidenceAcquisitionContractException;
import com.dndmaster.adventure.evidence.EvidenceSearchScope;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CrossContextHttpRuntimeEvidenceLocationLookupGatewayTest {
    private WireMockServer server;
    private final UUID ownerId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID packageId = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();
    private final UUID chunkId = UUID.randomUUID();

    @BeforeEach void startServer() { server = new WireMockServer(0); server.start(); }
    @AfterEach void stopServer() { server.stop(); }

    @Test
    void requests_same_page_chunks_in_the_exact_active_scope() {
        String locator = "page=4:chunk=anchor";
        server.stubFor(post(urlEqualTo("/internal/v1/evidence-locations/lookup"))
                .withHeader("X-Internal-Token", equalTo("internal-token"))
                .withRequestBody(equalToJson("""
                        {"ownerId":"%s","sessionId":"%s","scenarioPackageId":"%s",
                         "scope":[{"documentId":"%s","extractionVersion":7,"documentType":"RULEBOOK"}],
                         "locators":["%s"],"limit":4}
                        """.formatted(ownerId, sessionId, packageId, documentId, locator)))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"ownerId":"%s","sessionId":"%s","scenarioPackageId":"%s","candidates":[
                          {"chunkId":"%s","documentId":"%s","extractionVersion":7,"documentType":"RULEBOOK",
                           "locator":"page=4:chunk=neighbor","excerpt":"same page context"}]}
                        """.formatted(ownerId, sessionId, packageId, chunkId, documentId))));
        var gateway = gateway();

        List<RuntimeEvidence> result = gateway.findOnSamePages(scope(), List.of(anchor(locator)), 4);

        assertEquals(1, result.size());
        assertEquals(7, result.getFirst().extractionVersion());
        assertEquals("page=4:chunk=neighbor", result.getFirst().locator());
        assertEquals("same page context", result.getFirst().excerpt());
    }

    @Test
    void rejects_a_response_from_a_historical_extraction_version() {
        server.stubFor(post(urlEqualTo("/internal/v1/evidence-locations/lookup"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"ownerId":"%s","sessionId":"%s","scenarioPackageId":"%s","candidates":[
                          {"chunkId":"%s","documentId":"%s","extractionVersion":6,"documentType":"RULEBOOK",
                           "locator":"page=4:chunk=neighbor","excerpt":"historical context"}]}
                        """.formatted(ownerId, sessionId, packageId, chunkId, documentId))));

        assertThrows(EvidenceAcquisitionContractException.class,
                () -> gateway().findOnSamePages(scope(), List.of(anchor("page=4:chunk=anchor")), 4));
    }

    @Test
    void scopes_identical_locators_to_their_individual_documents() {
        UUID otherDocumentId = UUID.randomUUID();
        UUID otherChunkId = UUID.randomUUID();
        String locator = "page=1:chunk=anchor";
        server.stubFor(post(urlEqualTo("/internal/v1/evidence-locations/lookup"))
                .withRequestBody(matchingJsonPath("$.scope[0].documentId", equalTo(documentId.toString())))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"ownerId":"%s","sessionId":"%s","scenarioPackageId":"%s","candidates":[
                          {"chunkId":"%s","documentId":"%s","extractionVersion":7,"documentType":"RULEBOOK",
                           "locator":"page=1:chunk=neighbor","excerpt":"rule page"}]}
                        """.formatted(ownerId, sessionId, packageId, chunkId, documentId))));
        server.stubFor(post(urlEqualTo("/internal/v1/evidence-locations/lookup"))
                .withRequestBody(matchingJsonPath("$.scope[0].documentId", equalTo(otherDocumentId.toString())))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"ownerId":"%s","sessionId":"%s","scenarioPackageId":"%s","candidates":[
                          {"chunkId":"%s","documentId":"%s","extractionVersion":3,"documentType":"STORYBOOK",
                           "locator":"page=1:chunk=neighbor","excerpt":"story page"}]}
                        """.formatted(ownerId, sessionId, packageId, otherChunkId, otherDocumentId))));
        EvidenceSearchScope scope = new EvidenceSearchScope(ownerId, sessionId, packageId, "scene:cellar", "PLAYER_ACTION",
                List.of(new EvidenceSearchScope.Document(documentId, 7, "RULEBOOK"),
                        new EvidenceSearchScope.Document(otherDocumentId, 3, "STORYBOOK")), List.of());
        List<RuntimeEvidence> anchors = List.of(anchor(locator), new RuntimeEvidence(RuntimeEvidenceType.STORYBOOK,
                new KnowledgeDocumentId(otherDocumentId), 3, locator, "other anchor"));

        List<RuntimeEvidence> result = gateway().findOnSamePages(scope, anchors, 4);

        assertEquals(List.of(documentId, otherDocumentId), result.stream().map(item -> item.knowledgeDocumentId().value()).toList());
    }

    @Test
    void batches_all_selected_page_locators_for_the_same_document() {
        String firstLocator = "page=1:chunk=first";
        String secondLocator = "page=3:chunk=second";
        server.stubFor(post(urlEqualTo("/internal/v1/evidence-locations/lookup"))
                .withRequestBody(equalToJson("""
                        {"ownerId":"%s","sessionId":"%s","scenarioPackageId":"%s",
                         "scope":[{"documentId":"%s","extractionVersion":7,"documentType":"RULEBOOK"}],
                         "locators":["%s","%s"],"limit":4}
                        """.formatted(ownerId, sessionId, packageId, documentId, firstLocator, secondLocator)))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"ownerId":"%s","sessionId":"%s","scenarioPackageId":"%s","candidates":[
                          {"chunkId":"%s","documentId":"%s","extractionVersion":7,"documentType":"RULEBOOK",
                           "locator":"page=1:chunk=neighbor","excerpt":"first page context"},
                          {"chunkId":"%s","documentId":"%s","extractionVersion":7,"documentType":"RULEBOOK",
                           "locator":"page=3:chunk=neighbor","excerpt":"second page context"}]}
                        """.formatted(ownerId, sessionId, packageId, chunkId, documentId,
                                UUID.randomUUID(), documentId))));
        RuntimeEvidence secondAnchor = new RuntimeEvidence(RuntimeEvidenceType.RULEBOOK,
                new KnowledgeDocumentId(documentId), 7, secondLocator, "second anchor");

        List<RuntimeEvidence> result = gateway().findOnSamePages(scope(),
                List.of(anchor(firstLocator), secondAnchor), 4);

        assertEquals(List.of("first page context", "second page context"), result.stream().map(RuntimeEvidence::excerpt).toList());
    }

    private CrossContextHttpRuntimeEvidenceLocationLookupGateway gateway() {
        return new CrossContextHttpRuntimeEvidenceLocationLookupGateway(HttpClient.newHttpClient(),
                URI.create(server.baseUrl() + "/"), Duration.ofSeconds(2), new ObjectMapper(), "internal-token");
    }

    private EvidenceSearchScope scope() {
        return new EvidenceSearchScope(ownerId, sessionId, packageId, "scene:cellar", "PLAYER_ACTION",
                List.of(new EvidenceSearchScope.Document(documentId, 7, "RULEBOOK")), List.of());
    }

    private RuntimeEvidence anchor(String locator) {
        return new RuntimeEvidence(RuntimeEvidenceType.RULEBOOK, new KnowledgeDocumentId(documentId), 7, locator, "anchor");
    }
}
