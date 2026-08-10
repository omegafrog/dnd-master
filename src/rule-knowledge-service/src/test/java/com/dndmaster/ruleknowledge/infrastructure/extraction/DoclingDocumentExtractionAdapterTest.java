package com.dndmaster.ruleknowledge.infrastructure.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class DoclingDocumentExtractionAdapterTest {
    @Test
    void mapsEngineNeutralDocumentResponse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/extract", exchange -> {
                byte[] response = """
                        {"nodes":[{"id":"h1","type":"HEADING","page":2,"text":"Rules"}],"tables":[],"images":[],"warnings":[],"rawText":"Rules"}
                        """.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.start();
            var result = new DoclingDocumentExtractionAdapter(
                    "http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofSeconds(2))
                    .extract(RulebookFormat.PDF, new byte[] {1, 2});
            assertEquals("Rules", result.nodes().get(0).text());
            assertEquals(2, result.nodes().get(0).page());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mapsServerFailureToRetryableFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/extract", exchange -> { exchange.sendResponseHeaders(503, -1); exchange.close(); });
            server.start();
            var adapter = new DoclingDocumentExtractionAdapter(
                    "http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofSeconds(2));
            var failure = assertThrows(DoclingExtractionException.class,
                    () -> adapter.extract(RulebookFormat.PDF, new byte[] {1}));
            assertEquals(true, failure.retryable());
        } finally {
            server.stop(0);
        }
    }
}
