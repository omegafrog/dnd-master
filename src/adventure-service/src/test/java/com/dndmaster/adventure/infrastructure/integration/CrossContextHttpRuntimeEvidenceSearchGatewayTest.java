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
    void roundTrips_server_owned_key_and_provenance_for_story_and_rule_evidence() throws Exception {
        UUID storybookId = UUID.randomUUID();
        UUID rulebookId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        List<String> requests = new ArrayList<>();
        try (EvidenceServer server = new EvidenceServer(requests, storybookId, rulebookId)) {
            var gateway = new CrossContextHttpRuntimeEvidenceSearchGateway(
                    HttpClient.newHttpClient(), server.baseUri(), Duration.ofSeconds(2), new ObjectMapper());

            RuntimeEvidenceSearchRequest base = new RuntimeEvidenceSearchRequest(
                    new AdventureId(UUID.randomUUID()), new OwnerPlayerId(ownerId), new SessionId(UUID.randomUUID()),
                    UUID.randomUUID(), List.of(storybookId, rulebookId), null, "open the cellar",
                    RuntimeEvidenceType.STORYBOOK, 8, Map.of(storybookId, 12L, rulebookId, 4L), "stage-2", "MIXED");
            RuntimeEvidence story = gateway.search(base).getFirst();
            RuntimeEvidence rule = gateway.search(base.forType(RuntimeEvidenceType.RULEBOOK, 1)).getFirst();

            assertThat(story.citationKey()).isEqualTo("story-rat");
            assertThat(story.extractionVersion()).isEqualTo(12);
            assertThat(story.locator()).isEqualTo("page:4:block:2");
            assertThat(rule.citationKey()).isEqualTo("rule-grapple");
            assertThat(rule.extractionVersion()).isEqualTo(4);
            assertThat(requests).containsExactly(
                    "/internal/v1/story-sources/search", "/internal/v1/rule-evidence/search");
        }
    }

    private static final class EvidenceServer implements AutoCloseable {
        private final HttpServer server;

        private EvidenceServer(List<String> requests, UUID storybookId, UUID rulebookId) throws Exception {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                requests.add(path);
                String body = path.endsWith("story-sources/search")
                        ? "{\"evidence\":[{\"knowledgeDocumentId\":\"" + storybookId
                                + "\",\"extractionVersion\":12,\"locator\":\"page:4:block:2\",\"excerpt\":\"지하실에는 거대 쥐가 있습니다.\",\"score\":0.9,\"citationKey\":\"story-rat\",\"provenance\":{\"documentId\":\""
                                + storybookId + "\",\"extractionVersion\":12,\"pageNumber\":4,\"sectionPath\":[],\"bbox\":[],\"tableCell\":null,\"locator\":\"page:4:block:2\"}}]}"
                        : "{\"evidence\":[{\"rulebookId\":\"" + rulebookId
                                + "\",\"chunkId\":\"" + UUID.randomUUID() + "\",\"locator\":\"page:2\",\"excerpt\":\"잡기 판정은 서로 겨루는 판정입니다.\",\"score\":0.9,\"citationKey\":\"rule-grapple\",\"provenance\":{\"documentId\":\""
                                + rulebookId + "\",\"extractionVersion\":4,\"pageNumber\":2,\"sectionPath\":[],\"bbox\":[],\"tableCell\":null,\"locator\":\"page:2\"}}]}";
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
