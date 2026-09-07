package com.dndmaster.combatmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.combatmap.application.view.HttpMapImageEvidenceGateway;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

class HttpMapImageEvidenceGatewayTest {
    @Test
    void loadsImageAssetUsingDocumentAndLocator() throws Exception {
        byte[] image = new byte[] {1, 2, 3};
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v1/story-sources/", exchange -> {
            assertTrue(exchange.getRequestURI().getQuery().contains("locator=page+1"));
            assertEquals("secret", exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            exchange.getResponseHeaders().add("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, image.length);
            try (var output = exchange.getResponseBody()) { output.write(image); }
        });
        server.start();
        try {
            var evidence = new HttpMapImageEvidenceGateway(HttpClient.newHttpClient(),
                    java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"),
                    Duration.ofSeconds(2), "secret")
                    .load(UUID.randomUUID(), "page 1 image 1").orElseThrow();
            assertEquals("image/png", evidence.contentType());
            assertEquals("data:image/png;base64,AQID", evidence.dataUri());
        } finally {
            server.stop(0);
        }
    }
}
