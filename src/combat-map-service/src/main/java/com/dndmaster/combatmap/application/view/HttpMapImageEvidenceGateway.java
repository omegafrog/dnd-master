package com.dndmaster.combatmap.application.view;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/** 공용 자료 보관소에서 지도 이미지 자산을 가져오는 HTTP 어댑터. */
public final class HttpMapImageEvidenceGateway implements MapImageEvidencePort {
    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;
    private final String internalToken;

    public HttpMapImageEvidenceGateway(HttpClient client, URI baseUri, Duration timeout, String internalToken) {
        this.client = client;
        this.baseUri = baseUri;
        this.timeout = timeout;
        this.internalToken = internalToken == null ? "" : internalToken;
    }

    @Override
    public Optional<MapImageEvidence> load(UUID documentId, String locator) {
        if (documentId == null || locator == null || locator.isBlank()) return Optional.empty();
        String requestedLocator = pageLocator(locator);
        URI endpoint = baseUri.resolve("internal/v1/story-sources/" + documentId + "/assets?locator="
                + URLEncoder.encode(requestedLocator, StandardCharsets.UTF_8));
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("X-Internal-Token", internalToken)
                    .GET().build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 404) return Optional.empty();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("map image evidence failed with status " + response.statusCode());
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("image/png").split(";", 2)[0];
            return Optional.of(new MapImageEvidence(contentType, response.body()));
        } catch (IOException exception) {
            throw new IllegalStateException("map image evidence transport failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("map image evidence request interrupted", exception);
        }
    }

    private static String pageLocator(String locator) {
        java.util.regex.Matcher page = java.util.regex.Pattern.compile("(?i)\\bpage\\s+(\\d+)\\b").matcher(locator);
        return page.find() ? "page " + page.group(1) : locator;
    }
}
