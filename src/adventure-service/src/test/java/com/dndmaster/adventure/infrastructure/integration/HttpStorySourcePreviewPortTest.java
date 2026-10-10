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
                    List.of(Map.of("query", queries.get(0), "candidates", java.util.stream.IntStream.range(0, 6).mapToObj(index ->
                                    Map.of("documentId", doc, "extractionVersion", 3, "documentType", "RULEBOOK",
                                            "locator", "page:" + index, "excerpt", index == 0 ? "x".repeat(1500) : "Use an ability check"))
                                    .toList()),
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

            assertEquals(4, results.getFirst().size());
            assertEquals(1200, results.getFirst().getFirst().length());
            assertEquals(List.of("Apply the saving throw"), results.getLast());
            assertEquals(2, ((List<?>) captured.get().get("queries")).size());
            assertEquals(8, captured.get().get("denseLimit"));
            assertEquals(8, captured.get().get("bm25Limit"));
            assertEquals("RULEBOOK", ((Map<?, ?>) ((List<?>) captured.get().get("scope")).getFirst()).get("documentType"));
        } finally { server.stop(0); }
    }

    @Test
    void loads_only_bounded_story_candidates_returned_by_rag() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID bundle = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        var document = new ScenarioBundleDocumentSelection(new KnowledgeDocumentId(id),
                ScenarioBundleDocumentRole.MAIN_SCENARIO, () -> "INDEXED", "story.txt", "STORYBOOK", 2);
        var mapper = new ObjectMapper();
        var captured = new AtomicReference<Map<String, Object>>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/evidence-candidates/preparation-search-batch", exchange -> {
            var request = mapper.readTree(exchange.getRequestBody());
            captured.set(mapper.convertValue(request, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}));
            String query = request.path("queries").get(0).asText();
            byte[] body = mapper.writeValueAsBytes(Map.of("ownerId", owner, "scenarioSourceBundleId", bundle,
                    "results", List.of(Map.of("query", query, "candidates", java.util.stream.IntStream.range(0, 17)
                            .mapToObj(index -> Map.of("documentId", id, "extractionVersion", 2, "documentType", "STORYBOOK",
                                    "locator", index < 2 ? "page:2" : "page:" + (index + 2),
                                    "excerpt", index == 0 ? "ending" : index == 1 ? "duplicate ending"
                                            : index == 2 ? "x".repeat(1500) : "candidate " + index))
                            .toList()))));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            var port = new HttpStorySourcePreviewPort(HttpClient.newHttpClient(),
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"), Duration.ofSeconds(2), mapper, "internal-token");

            var passages = port.load(document, owner, bundle);

            assertEquals(14, passages.size());
            assertEquals(List.of("page:2", "page:4"), passages.stream()
                    .map(item -> item.sourceRef().locator()).limit(2).toList());
            assertEquals("ending", passages.getFirst().text());
            assertEquals(1200, passages.get(1).text().length());
            assertEquals(15, captured.get().get("denseLimit"));
            assertEquals(15, captured.get().get("bm25Limit"));
            assertEquals("STORYBOOK", ((Map<?, ?>) ((List<?>) captured.get().get("scope")).getFirst()).get("documentType"));
            assertEquals(1, ((List<?>) captured.get().get("queries")).size());
        } finally {
            server.stop(0);
        }
    }

}
