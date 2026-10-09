package com.dndmaster.adventure.infrastructure.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class HttpStorySourcePreviewPortTest {
    @Test
    void batches_situation_rule_queries_against_rulebooks_only() throws Exception {
        UUID owner = UUID.randomUUID(), bundle = UUID.randomUUID(), doc = UUID.randomUUID();
        var mapper = new ObjectMapper();
        var captured = new AtomicReference<Map<String, Object>>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/evidence-candidates/preparation-search-batch", exchange -> {
            var request = mapper.readTree(exchange.getRequestBody());
            captured.set(mapper.convertValue(request, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}));
            List<String> queries = new java.util.ArrayList<>();
            request.path("queries").forEach(query -> queries.add(query.asText()));
            byte[] body = mapper.writeValueAsBytes(Map.of("ownerId", owner, "scenarioSourceBundleId", bundle, "results",
                    List.of(Map.of("query", queries.get(0), "candidates", List.of(Map.of("documentId", doc, "extractionVersion", 3,
                                    "documentType", "RULEBOOK", "locator", "page:1", "excerpt", "Use an ability check"))),
                            Map.of("query", queries.get(1), "candidates", List.of(Map.of("documentId", doc, "extractionVersion", 3,
                                    "documentType", "RULEBOOK", "locator", "page:2", "excerpt", "Apply the saving throw"))))));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            var port = new HttpStorySourcePreviewPort(HttpClient.newHttpClient(), URI.create("http://127.0.0.1:"
                    + server.getAddress().getPort() + "/"), Duration.ofSeconds(2), mapper, "internal-token");
            var rulebook = new ScenarioBundleDocumentSelection(new KnowledgeDocumentId(doc), ScenarioBundleDocumentRole.RULEBOOK,
                    () -> "INDEXED", "basic.pdf", "RULEBOOK", 3);
            var sourceRef = new com.dndmaster.adventure.domain.scenario.ScenarioSourceReference(new KnowledgeDocumentId(doc), 3, "page:1");
            var situations = List.of(new com.dndmaster.adventure.domain.scenario.RequiredSituation("a", "Reach the cellar", List.of(), List.of(sourceRef)),
                    new com.dndmaster.adventure.domain.scenario.RequiredSituation("b", "Face the creature", List.of("a"), List.of(sourceRef)));

            var results = port.findApplicableRules(List.of(rulebook), owner, bundle, situations);

            assertEquals(List.of(List.of("Use an ability check"), List.of("Apply the saving throw")), results);
            assertEquals(2, ((List<?>) captured.get().get("queries")).size());
            assertEquals("RULEBOOK", ((Map<?, ?>) ((List<?>) captured.get().get("scope")).getFirst()).get("documentType"));
        } finally { server.stop(0); }
    }

    @Test
    void loads_every_indexed_chunk_and_rejects_a_page_that_does_not_match_declared_coverage() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID bundle = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        var document = new ScenarioBundleDocumentSelection(new KnowledgeDocumentId(id),
                ScenarioBundleDocumentRole.MAIN_SCENARIO, () -> "INDEXED", "story.txt", "STORYBOOK", 2);
        var mapper = new ObjectMapper();
        var page = new AtomicReference<Map<String, Object>>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/evidence-candidates/preparation-search-batch", exchange -> {
            var request = mapper.readTree(exchange.getRequestBody());
            String query = request.path("queries").get(0).asText();
            byte[] body = mapper.writeValueAsBytes(Map.of("ownerId", owner, "scenarioSourceBundleId", bundle,
                    "results", List.of(Map.of("query", query, "candidates", List.of(Map.of("documentId", id,
                            "extractionVersion", 2, "documentType", "STORYBOOK", "locator", "page:2", "excerpt", "ending"))))));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.createContext("/internal/v1/rag-chunks/preparation", exchange -> {
            byte[] body = mapper.writeValueAsBytes(page.get());
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            var port = new HttpStorySourcePreviewPort(HttpClient.newHttpClient(),
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"), Duration.ofSeconds(2), mapper, "internal-token");
            page.set(Map.of("ownerId", owner, "scenarioSourceBundleId", bundle, "documentId", id,
                    "extractionVersion", 2, "totalCount", 2, "offset", 0,
                    "chunks", List.of(Map.of("chunkId", UUID.randomUUID(), "sequence", 0, "content", "beginning",
                                    "originalLocator", "page:1", "pageNumber", 1, "sectionPath", List.of()),
                            Map.of("chunkId", UUID.randomUUID(), "sequence", 1, "content", "ending",
                                    "originalLocator", "page:2", "pageNumber", 2, "sectionPath", List.of()))));
            assertEquals(List.of("page:2", "page:1"), port.load(document, owner, bundle).stream()
                    .map(item -> item.sourceRef().locator()).toList());

            page.set(Map.of("ownerId", owner, "scenarioSourceBundleId", bundle, "documentId", id,
                    "extractionVersion", 2, "totalCount", 3, "offset", 0,
                    "chunks", List.of(Map.of("chunkId", UUID.randomUUID(), "sequence", 0, "content", "beginning",
                            "originalLocator", "page:1", "pageNumber", 1, "sectionPath", List.of()))));
            assertThrows(IllegalStateException.class, () -> port.load(document, owner, bundle));
        } finally {
            server.stop(0);
        }
    }
}
