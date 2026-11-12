package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.scenario.preparation.ScenarioMapImagePort;
import com.dndmaster.adventure.domain.scenario.MapDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import org.slf4j.LoggerFactory;

public final class HttpScenarioMapImagePort implements ScenarioMapImagePort {
    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final String internalToken;

    public HttpScenarioMapImagePort(HttpClient client, URI baseUri, Duration timeout,
            ObjectMapper mapper, String internalToken) {
        this.client = Objects.requireNonNull(client);
        this.baseUri = Objects.requireNonNull(baseUri);
        this.timeout = Objects.requireNonNull(timeout);
        this.mapper = Objects.requireNonNull(mapper);
        this.internalToken = Objects.requireNonNull(internalToken);
    }

    @Override public String load(MapDefinition map) {
        var source = Objects.requireNonNull(map.source(), "map source is required");
        String context = "mapDefinitionId=" + map.id() + " sourceDocumentId="
                + source.knowledgeDocumentId().value() + " locatorChars=" + map.assetLocator().length();
        long started = com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.begin(
                LoggerFactory.getLogger(getClass()), "initial_situation_map_image", context);
        try {
            String query = "internal/v1/combat-map-source-image?sourceDocumentId="
                    + source.knowledgeDocumentId().value() + "&locator="
                    + URLEncoder.encode(map.assetLocator(), StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(query)).timeout(timeout)
                    .header("X-Internal-Token", internalToken).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IllegalStateException("map image retrieval failed with status " + response.statusCode());
            SourceMapImage image = mapper.readValue(response.body(), SourceMapImage.class);
            if (image.imageDataUri() == null || !image.imageDataUri().startsWith("data:image/"))
                throw new IllegalStateException("map image retrieval returned no image data");
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.complete(
                    LoggerFactory.getLogger(getClass()), "initial_situation_map_image",
                    context + " imageDataUriChars=" + image.imageDataUri().length() + " result=attached", started);
            return image.imageDataUri();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            var wrapped = new IllegalStateException("map image retrieval interrupted", failure);
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fail(
                    LoggerFactory.getLogger(getClass()), "initial_situation_map_image",
                    context + " recoveryAction=reject_situation_preparation", started, wrapped);
            throw wrapped;
        } catch (IOException | RuntimeException failure) {
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fail(
                    LoggerFactory.getLogger(getClass()), "initial_situation_map_image",
                    context + " recoveryAction=reject_situation_preparation", started, failure);
            throw new IllegalStateException("map image retrieval failed", failure);
        }
    }

    private record SourceMapImage(String contentType, String imageDataUri) {}
}
