package com.dndmaster.adventure.infrastructure.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.dndmaster.adventure.application.runtime.RuntimeEvidence;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceSearchRequest;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceType;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CrossContextHttpRuntimeEvidenceSearchGatewayTest {
    @Test
    void roundTrips_fused_candidates_for_story_and_rule_evidence() throws Exception {
        UUID storybookId = UUID.randomUUID();
        UUID rulebookId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        List<String> requests = new ArrayList<>();
        List<String> tokens = new ArrayList<>();
        List<String> bodies = new ArrayList<>();
        try (EvidenceServer server = new EvidenceServer(requests, tokens, bodies, storybookId, rulebookId, ownerId, sessionId, packageId)) {
            var gateway = new CrossContextHttpRuntimeEvidenceSearchGateway(
                    HttpClient.newHttpClient(), server.baseUri(), Duration.ofSeconds(2), new ObjectMapper(), "internal-token");

            RuntimeEvidenceSearchRequest base = new RuntimeEvidenceSearchRequest(
                    new AdventureId(UUID.randomUUID()), new OwnerPlayerId(ownerId), new SessionId(sessionId),
                    packageId, List.of(storybookId, rulebookId), null, "open the cellar",
                    RuntimeEvidenceType.STORYBOOK, 8, Map.of(storybookId, 12L, rulebookId, 4L), "stage-2", "MIXED");
            RuntimeEvidence story = gateway.search(base).getFirst();
            RuntimeEvidence rule = gateway.search(base.forType(RuntimeEvidenceType.RULEBOOK, 1)).getFirst();

            assertThat(story.citationKey()).isEqualTo("STORYBOOK:" + storybookId + ":12:page:4:block:2");
            assertThat(story.extractionVersion()).isEqualTo(12);
            assertThat(story.locator()).isEqualTo("page:4:block:2");
            assertThat(rule.citationKey()).isEqualTo("RULEBOOK:" + rulebookId + ":4:page:2");
            assertThat(rule.extractionVersion()).isEqualTo(4);
            assertThat(requests).containsExactly(
                    "/internal/v1/evidence-candidates/search", "/internal/v1/evidence-candidates/search");
            assertThat(tokens).containsExactly("internal-token", "internal-token");
            var payload = new ObjectMapper().readTree(bodies.getFirst());
            var payloadFields = new java.util.TreeSet<String>();
            payload.fieldNames().forEachRemaining(payloadFields::add);
            assertThat(payloadFields)
                    .containsExactly("actionIntent", "activeLocators", "denseLimit", "excludedEvidenceIds", "ownerId",
                            "query", "scenarioPackageId", "scope", "sessionId", "stageKey");
            assertThat(payload.path("ownerId").asText()).isEqualTo(ownerId.toString());
            assertThat(payload.path("sessionId").asText()).isEqualTo(sessionId.toString());
            assertThat(payload.path("scenarioPackageId").asText()).isEqualTo(packageId.toString());
            assertThat(payload.path("stageKey").asText()).isEqualTo("stage-2");
            assertThat(payload.path("actionIntent").asText()).isEqualTo("MIXED");
            assertThat(payload.path("scope")).hasSize(2);
            assertThat(payload.path("activeLocators")).isEmpty();
            assertThat(payload.path("excludedEvidenceIds")).isEmpty();
            assertThat(payload.path("denseLimit").asInt()).isEqualTo(30);
            assertThat(payload.path("query").asText()).isEqualTo("open the cellar");
        }
    }

    @Test
    void skips_search_when_a_rulebook_only_package_has_no_storybook_scope() throws Exception {
        List<String> requests = new ArrayList<>();
        List<String> tokens = new ArrayList<>();
        try (EvidenceServer server = new EvidenceServer(requests, tokens, new ArrayList<>(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())) {
            var gateway = new CrossContextHttpRuntimeEvidenceSearchGateway(
                    HttpClient.newHttpClient(), server.baseUri(), Duration.ofSeconds(2), new ObjectMapper(), "internal-token");
            RuntimeEvidenceSearchRequest request = new RuntimeEvidenceSearchRequest(
                    new AdventureId(UUID.randomUUID()), new OwnerPlayerId(UUID.randomUUID()), new SessionId(UUID.randomUUID()),
                    UUID.randomUUID(), List.of(), null, "open the cellar", RuntimeEvidenceType.STORYBOOK, 8);

            assertThat(gateway.search(request)).isEmpty();
            assertThat(requests).isEmpty();
            assertThat(tokens).isEmpty();
        }
    }

    private static final class EvidenceServer implements AutoCloseable {
        private final HttpServer server;

        private EvidenceServer(List<String> requests, List<String> tokens, List<String> bodies, UUID storybookId, UUID rulebookId,
                               UUID ownerId, UUID sessionId, UUID packageId) throws Exception {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                requests.add(path);
                tokens.add(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
                bodies.add(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                boolean story = path.endsWith("evidence-candidates/search") && requests.size() == 1;
                String body = story
                        ? "{\"ownerId\":\"" + ownerId + "\",\"sessionId\":\"" + sessionId + "\",\"scenarioPackageId\":\"" + packageId + "\",\"candidates\":[{\"chunkId\":\"" + UUID.randomUUID()
                                + "\",\"documentId\":\"" + storybookId + "\",\"extractionVersion\":12,\"documentType\":\"STORYBOOK\",\"locator\":\"page:4:block:2\",\"excerpt\":\"지하실에는 거대 쥐가 있습니다.\"}]}"
                        : "{\"ownerId\":\"" + ownerId + "\",\"sessionId\":\"" + sessionId + "\",\"scenarioPackageId\":\"" + packageId + "\",\"candidates\":[{\"chunkId\":\"" + UUID.randomUUID()
                                + "\",\"documentId\":\"" + rulebookId + "\",\"extractionVersion\":4,\"documentType\":\"RULEBOOK\",\"locator\":\"page:2\",\"excerpt\":\"잡기 판정은 서로 겨루는 판정입니다.\"}]}";
                exchange.sendResponseHeaders(200, body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
                try (var output = exchange.getResponseBody()) {
                    output.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            });
            server.start();
        }

        private URI baseUri() {
            return URI.create("http://localhost:" + server.getAddress().getPort() + "/");
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
