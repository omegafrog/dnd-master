package com.dndmaster.adventure.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentLookupPort;
import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
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

class CrossContextHttpKnowledgeDocumentLookupGatewayTest {
    private WireMockServer wireMock;
    private CrossContextHttpKnowledgeDocumentLookupGateway gateway;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(0);
        wireMock.start();
        gateway = new CrossContextHttpKnowledgeDocumentLookupGateway(
                HttpClient.newHttpClient(),
                URI.create(wireMock.baseUrl() + "/"),
                Duration.ofSeconds(2),
                new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void mapsNeedsReviewDocumentsWithoutFailingTheOwnedDocumentList() {
        UUID owner = UUID.randomUUID();
        UUID needsReviewId = UUID.randomUUID();
        UUID readyId = UUID.randomUUID();
        wireMock.stubFor(get(urlEqualTo("/internal/v1/rulebooks?ownerId=" + owner))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"ownerId":"%s","rulebooks":[
                                  {"knowledgeDocumentId":"%s","status":"NEEDS_REVIEW","documentType":"STORYBOOK","originalFilename":"review.pdf","extractionVersion":1},
                                  {"knowledgeDocumentId":"%s","status":"INDEXED","documentType":"RULEBOOK","originalFilename":"rules.pdf","extractionVersion":2}
                                ]}
                                """.formatted(owner, needsReviewId, readyId))));

        List<KnowledgeDocumentLookupPort.KnowledgeDocumentRecord> documents = gateway.findOwnedDocuments(owner);

        assertEquals(2, documents.size());
        assertEquals(new KnowledgeDocumentId(needsReviewId), documents.getFirst().knowledgeDocumentId());
        assertEquals(KnowledgeDocumentStatus.NEEDS_REVIEW, documents.getFirst().status());
        assertEquals(KnowledgeDocumentStatus.INDEXED, documents.get(1).status());
    }
}
