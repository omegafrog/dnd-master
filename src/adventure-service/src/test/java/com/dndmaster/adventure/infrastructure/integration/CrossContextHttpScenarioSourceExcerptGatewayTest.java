package com.dndmaster.adventure.infrastructure.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus;
import com.dndmaster.adventure.application.scenario.compilation.ResolutionExtractionPort;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.OwnerPlayerId;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundle;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundleRevision;
import com.dndmaster.adventure.domain.scenario.RulebookEdition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CrossContextHttpScenarioSourceExcerptGatewayTest {
    @Test
    void loadsRulebookAndStorybookFromTheSamePublishedEvidenceContract() throws Exception {
        UUID storybookId = UUID.randomUUID();
        UUID rulebookId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        List<String> paths = new ArrayList<>();
        List<String> requestBodies = new ArrayList<>();
        UUID bundleId = UUID.randomUUID();
        try (EvidenceServer server = new EvidenceServer(paths, requestBodies, ownerId, bundleId, storybookId, rulebookId)) {
            var bundle = ScenarioSourceBundle.create(new ScenarioBundleId(bundleId),
                    new OwnerPlayerId(ownerId), "Test", RulebookEdition.DND_5E_2014,
                    new ScenarioSourceBundleRevision(1, List.of(
                            selection(storybookId, ScenarioBundleDocumentRole.MAIN_SCENARIO, "STORYBOOK"),
                            selection(rulebookId, ScenarioBundleDocumentRole.RULEBOOK, "RULEBOOK"))));

            List<ResolutionExtractionPort.SourceExcerpt> excerpts = new CrossContextHttpScenarioSourceExcerptGateway(
                    HttpClient.newHttpClient(), server.baseUri(), Duration.ofSeconds(2), new ObjectMapper(), "internal-token").load(bundle);

            assertThat(excerpts).hasSize(2);
            assertThat(excerpts).allSatisfy(excerpt -> {
                assertThat(excerpt.provenance().documentId()).isEqualTo(excerpt.documentId());
                assertThat(excerpt.provenance().extractionVersion()).isEqualTo(excerpt.extractionVersion());
                assertThat(excerpt.provenance().pageNumber()).isEqualTo(3);
                assertThat(excerpt.provenance().sectionPath()).containsExactly("Chapter", "Checks");
                assertThat(excerpt.provenance().tableCell()).isEqualTo("table-1:r2:c1");
            });
            assertThat(paths).containsExactly(
                    "/internal/v1/evidence-candidates/preparation-search",
                    "/internal/v1/evidence-candidates/preparation-search");
            assertThat(paths).doesNotContain("/api/v1/rulebooks/" + rulebookId + "/source-preview");
            assertThat(requestBodies).anyMatch(body -> body.contains("how player actions are resolved")
                    && body.contains("scenarioSourceBundleId") && !body.contains("sessionId")
                    && !body.contains("scenarioPackageId"));
            assertThat(requestBodies).anyMatch(body -> body.contains("Extract source-grounded rule procedures.")
                    && body.contains("\"documentType\":\"RULEBOOK\"")
                    && body.contains("\"extractionVersion\":7")
                    && !body.contains("sessionId") && !body.contains("scenarioPackageId"));
        }
    }

    private static ScenarioBundleDocumentSelection selection(
            UUID documentId, ScenarioBundleDocumentRole role, String documentType) {
        return new ScenarioBundleDocumentSelection(new KnowledgeDocumentId(documentId), role,
                KnowledgeDocumentStatus.INDEXED, documentType + ".pdf", documentType, 7);
    }

    private static final class EvidenceServer implements AutoCloseable {
        private final HttpServer server;

        private EvidenceServer(List<String> paths, List<String> requestBodies, UUID ownerId, UUID bundleId, UUID storybookId, UUID rulebookId) throws IOException {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/", exchange -> {
                paths.add(exchange.getRequestURI().getPath());
                requestBodies.add(new String(exchange.getRequestBody().readAllBytes()));
                String path = exchange.getRequestURI().getPath();
                String requestBody = requestBodies.getLast();
                String body = switch (path) {
                    case "/internal/v1/evidence-candidates/preparation-search" -> """
                            {"ownerId":"%s","scenarioSourceBundleId":"%s","candidates":[{"chunkId":"%s","documentId":"%s","extractionVersion":7,"documentType":"%s","locator":"page=3;block=b7","excerpt":"%s evidence","provenance":{"pageNumber":3,"sectionPath":["Chapter","Checks"],"bbox":[10,20,100,140],"tableCell":"table-1:r2:c1","originalLocator":"page=3;block=b7"}}]}
                            """.formatted(ownerId, bundleId, UUID.randomUUID(),
                                    requestBody.contains("\"documentType\":\"RULEBOOK\"") ? rulebookId : storybookId,
                                    requestBody.contains("\"documentType\":\"RULEBOOK\"") ? "RULEBOOK" : "STORYBOOK",
                                    requestBody.contains("\"documentType\":\"RULEBOOK\"") ? "rule" : "story",
                                    requestBody.contains("\"documentType\":\"RULEBOOK\"") ? rulebookId : storybookId);
                    default -> "{}";
                };
                exchange.sendResponseHeaders(path.contains("search") ? 200 : 404, body.getBytes().length);
                try (var output = exchange.getResponseBody()) { output.write(body.getBytes()); }
            });
            server.start();
        }

        private URI baseUri() { return URI.create("http://localhost:" + server.getAddress().getPort() + "/"); }

        @Override public void close() { server.stop(0); }
    }
}
