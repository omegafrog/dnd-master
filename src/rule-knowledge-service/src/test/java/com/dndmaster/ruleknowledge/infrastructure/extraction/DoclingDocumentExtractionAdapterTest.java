package com.dndmaster.ruleknowledge.infrastructure.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedDocument;
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
    void preservesVersionedNormalizedDocumentFieldsAndProjectsCompatibilityResult() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/extract", exchange -> {
                byte[] response = """
                        {
                          "schemaVersion":"normalized-document.v1",
                          "extractor":"docling",
                          "extractorVersion":"2.1",
                          "sourceIdentity":"sha256:abc",
                          "pages":[{"number":2,"width":612,"height":792}],
                          "elements":[{
                            "id":"e1","type":"HEADING","text":"Rules","page":2,"order":0,
                            "parentId":"root","parserLevel":1,
                            "sourceSpan":{"sourceId":"e1","page":2,"order":0,"start":0,"end":5,
                              "bbox":{"left":1,"top":2,"right":30,"bottom":12}}
                          }],
                          "tables":[{"id":"t1","page":2,"rows":[["A","B"]]}],
                          "pictures":[{"id":"p1","page":2,"mimeType":"image/png",
                            "bbox":{"left":3,"top":4,"right":8,"bottom":9}}],
                          "outlines":[{"id":"o1","title":"Rules","level":1,"locator":"2"}],
                          "parserRelations":[{"childId":"e1","parentId":"root","level":1}],
                          "rawText":"Rules"
                        }
                        """.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.start();
            var adapter = new DoclingDocumentExtractionAdapter(
                    "http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofSeconds(2));
            NormalizedDocument normalized = adapter.extractNormalized(RulebookFormat.PDF, new byte[] {1, 2});

            assertEquals("normalized-document.v1", normalized.schemaVersion());
            assertEquals("sha256:abc", normalized.sourceIdentity());
            assertEquals(1, normalized.elements().size());
            assertEquals("e1", normalized.elements().get(0).sourceSpan().sourceId());
            assertEquals(1, normalized.outlines().size());
            assertEquals(1, normalized.parserRelations().size());
            assertEquals(1, adapter.extract(RulebookFormat.PDF, new byte[] {1, 2}).nodes().size());
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
