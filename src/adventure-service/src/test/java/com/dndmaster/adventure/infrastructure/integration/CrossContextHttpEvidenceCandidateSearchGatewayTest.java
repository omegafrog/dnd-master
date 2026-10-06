package com.dndmaster.adventure.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void reports_the_endpoint_and_io_cause_class_without_the_cause_message() {
        URI unavailableBaseUri = URI.create(server.baseUrl() + "/");
        server.stop();
        var gateway = new CrossContextHttpEvidenceCandidateSearchGateway(HttpClient.newHttpClient(), unavailableBaseUri,
                Duration.ofSeconds(2), new ObjectMapper(), "internal-token");
        var scope = new EvidenceSearchScope(ownerId, sessionId, scenarioPackageId, "runtime", "MIXED",
                List.of(new EvidenceSearchScope.Document(documentId, 7, "RULEBOOK")), List.of("p:3"));

        var failure = assertThrows(com.dndmaster.adventure.evidence.EvidenceAcquisitionTransientException.class,
                () -> gateway.search(new EvidenceCandidateSearchRequest(
                        new EvidenceAcquisitionRequest("PLAYER_ACTION", "attack question", List.of(), scope), "attack question", 0)));

        assertEquals("evidence candidate search failed endpoint=/internal/v1/evidence-candidates/search causeClass=ConnectException",
                failure.getMessage());
    }

    @Test
    void searches_base_and_supplemental_rules_independently_with_the_same_turn_query() {
        UUID storybookId = UUID.randomUUID();
        UUID storyChunkId = UUID.randomUUID();
        UUID ruleChunkId = UUID.randomUUID();
        String query = "행동: 조용히 창고 안을 살펴본다. 현재 상황: 낡은 창고. 관련 판정 규칙이 있는지 찾는다.";
        String envelope = "\"ownerId\":\"%s\",\"sessionId\":\"%s\",\"scenarioPackageId\":\"%s\"";
        server.stubFor(post(urlEqualTo("/internal/v1/evidence-candidates/search"))
                .withRequestBody(matchingJsonPath("$.scope[0].documentType", equalTo("RULEBOOK")))
                .withRequestBody(matchingJsonPath("$.denseLimit", equalTo("15")))
                .withRequestBody(matchingJsonPath("$.bm25Limit", equalTo("15")))
                .withRequestBody(matchingJsonPath("$.query", equalTo(query)))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(("{" + envelope
                        + ",\"candidates\":[{\"chunkId\":\"%s\",\"documentId\":\"%s\",\"extractionVersion\":7,\"documentType\":\"RULEBOOK\",\"locator\":\"p:3\",\"excerpt\":\"base rule\"}]}"
                        ).formatted(ownerId, sessionId, scenarioPackageId, ruleChunkId, documentId))));
        server.stubFor(post(urlEqualTo("/internal/v1/evidence-candidates/search"))
                .withRequestBody(matchingJsonPath("$.scope[0].documentType", equalTo("STORYBOOK")))
                .withRequestBody(matchingJsonPath("$.denseLimit", equalTo("15")))
                .withRequestBody(matchingJsonPath("$.bm25Limit", equalTo("15")))
                .withRequestBody(matchingJsonPath("$.query", equalTo(query)))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(("{" + envelope
                        + ",\"candidates\":[{\"chunkId\":\"%s\",\"documentId\":\"%s\",\"extractionVersion\":4,\"documentType\":\"STORYBOOK\",\"locator\":\"p:8\",\"excerpt\":\"supplemental rule\"}]}"
                        ).formatted(ownerId, sessionId, scenarioPackageId, storyChunkId, storybookId))));
        var gateway = new CrossContextHttpEvidenceCandidateSearchGateway(HttpClient.newHttpClient(), URI.create(server.baseUrl() + "/"),
                Duration.ofSeconds(2), new ObjectMapper(), "internal-token");
        var scope = new EvidenceSearchScope(ownerId, sessionId, scenarioPackageId, "scene:warehouse", "PLAYER_ACTION",
                List.of(new EvidenceSearchScope.Document(documentId, 7, "RULEBOOK"),
                        new EvidenceSearchScope.Document(storybookId, 4, "STORYBOOK")), List.of());

        var result = gateway.search(new EvidenceCandidateSearchRequest(
                new EvidenceAcquisitionRequest("PLAYER_ACTION", query, List.of(), scope), query, 0));

        assertEquals(List.of(ruleChunkId, storyChunkId), result.stream().map(item -> item.id()).toList());
        assertEquals(2, server.getAllServeEvents().size());
        assertEquals(List.of(query, query), server.getAllServeEvents().stream()
                .map(event -> {
                    try { return new ObjectMapper().readTree(event.getRequest().getBodyAsString()).path("query").asText(); }
                    catch (Exception failure) { throw new IllegalStateException(failure); }
                }).toList());
    }
}
