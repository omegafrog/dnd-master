package com.dndmaster.adventure.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.evidence.EvidenceAcquisitionRequest;
import com.dndmaster.adventure.evidence.EvidenceCandidateSearchRequest;
import com.dndmaster.adventure.evidence.EvidenceSearchScope;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CrossContextHttpEvidenceCandidateSearchGatewayTest {
    private WireMockServer server;
    private final UUID ownerId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID scenarioPackageId = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();
    private final UUID chunkId = UUID.randomUUID();

    @BeforeEach void startServer() { server = new WireMockServer(0); server.start(); }
    @AfterEach void stopServer() { server.stop(); }

    @Test
    void forwards_the_server_confirmed_session_scope_and_rejects_unscoped_results() {
        server.stubFor(post(urlEqualTo("/internal/v1/evidence-candidates/search"))
                .withHeader("X-Internal-Token", equalTo("internal-token"))
                .withRequestBody(equalToJson("""
                        {"ownerId":"%s","sessionId":"%s","scenarioPackageId":"%s","stageKey":"runtime",
                         "actionIntent":"MIXED","scope":[{"documentId":"%s","extractionVersion":7,"documentType":"RULEBOOK"}],
                         "activeLocators":["p:3"],"query":"attack question","denseLimit":30,"bm25Limit":30}
                        """.formatted(ownerId, sessionId, scenarioPackageId, documentId)))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"ownerId":"%s","sessionId":"%s","scenarioPackageId":"%s","candidates":[
                          {"chunkId":"%s","documentId":"%s","extractionVersion":7,"documentType":"RULEBOOK","locator":"p:3","excerpt":"rule"}
                        ]}
                        """.formatted(ownerId, sessionId, scenarioPackageId, chunkId, documentId))));
        var gateway = new CrossContextHttpEvidenceCandidateSearchGateway(HttpClient.newHttpClient(), URI.create(server.baseUrl() + "/"),
                Duration.ofSeconds(2), new ObjectMapper(), "internal-token");
        var scope = new EvidenceSearchScope(ownerId, sessionId, scenarioPackageId, "runtime", "MIXED",
                List.of(new EvidenceSearchScope.Document(documentId, 7, "RULEBOOK")), List.of("p:3"));

        var result = gateway.search(new EvidenceCandidateSearchRequest(
                new EvidenceAcquisitionRequest("PLAYER_ACTION", "attack question", List.of(), scope), "attack question", 0));

        assertEquals(List.of(chunkId), result.stream().map(item -> item.id()).toList());
        assertEquals(documentId.toString(), result.getFirst().documentId());
    }
}
