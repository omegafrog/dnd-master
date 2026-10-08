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
import org.junit.jupiter.api.Test;

class HttpStorySourcePreviewPortTest {
    @Test
    void reads_all_located_original_passages_and_rejects_incomplete_preview() throws Exception {
        UUID id = UUID.randomUUID();
        var document = new ScenarioBundleDocumentSelection(new KnowledgeDocumentId(id),
                ScenarioBundleDocumentRole.MAIN_SCENARIO, () -> "INDEXED", "story.txt", "STORYBOOK", 2);
        var mapper = new ObjectMapper();
        var preview = new java.util.concurrent.atomic.AtomicReference<Map<String, Object>>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/rulebooks/" + id + "/source-preview", exchange -> {
            byte[] body = mapper.writeValueAsBytes(preview.get());
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            var port = new HttpStorySourcePreviewPort(HttpClient.newHttpClient(),
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"),
                    Duration.ofSeconds(2), mapper, "internal-token");
            var spans = List.of(Map.of("locator", "page:1", "text", "The keeper speaks"),
                    Map.of("locator", "page:2", "text", "Then find the bell"));
            preview.set(Map.of("knowledgeDocumentId", id, "documentType", "STORYBOOK", "status", "INDEXED",
                    "content", "The keeper speaks\n\nThen find the bell", "extractionVersion", 2,
                    "warnings", List.of(), "spans", spans));
            assertEquals(List.of("page:1", "page:2"), port.load(document).stream()
                    .map(item -> item.sourceRef().locator()).toList());

            preview.set(Map.of("knowledgeDocumentId", id, "documentType", "STORYBOOK", "status", "INDEXED",
                    "content", "The keeper speaks\n\nThen find the bell", "extractionVersion", 2,
                    "warnings", List.of("page 2 could not be extracted"), "spans", spans));
            assertThrows(IllegalStateException.class, () -> port.load(document));
            preview.set(Map.of("knowledgeDocumentId", id, "documentType", "STORYBOOK", "status", "INDEXED",
                    "content", "The keeper speaks\n\nThen find the bell", "extractionVersion", 2,
                    "warnings", List.of(), "spans", spans.subList(0, 1)));
            assertThrows(IllegalStateException.class, () -> port.load(document));
            preview.set(Map.of("knowledgeDocumentId", id, "documentType", "STORYBOOK", "status", "INDEXED",
                    "content", "The keeper speaks\n\nThen find the bell", "extractionVersion", 3,
                    "warnings", List.of(), "spans", spans));
            assertThrows(IllegalStateException.class, () -> port.load(document));
        } finally {
            server.stop(0);
        }
    }
}
