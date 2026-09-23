package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.runtime.RuntimeCharacterSheetReadException;
import com.dndmaster.adventure.application.runtime.RuntimeCharacterSheetReadPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/** Existing internal Character Management /runtime read, without a local sheet cache. */
public final class HttpRuntimeCharacterSheetReadPort implements RuntimeCharacterSheetReadPort {
    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;
    private final String internalToken;
    private final ObjectMapper mapper = new ObjectMapper();

    public HttpRuntimeCharacterSheetReadPort(HttpClient client, URI baseUri, Duration timeout, String internalToken) {
        this.client = Objects.requireNonNull(client);
        this.baseUri = Objects.requireNonNull(baseUri);
        this.timeout = Objects.requireNonNull(timeout);
        this.internalToken = Objects.requireNonNull(internalToken);
    }

    @Override
    public String read(UUID characterSheetId) {
        Objects.requireNonNull(characterSheetId);
        try {
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(
                    "internal/v1/character-sheets/" + characterSheetId + "/runtime"))
                    .timeout(timeout).header("X-Internal-Token", internalToken).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new RuntimeCharacterSheetReadException("current character sheet read failed: " + response.statusCode());
            }
            JsonNode sheet = mapper.readTree(response.body());
            if (sheet == null || !sheet.isObject()
                    || !characterSheetId.toString().equals(sheet.path("characterSheetId").asText())
                    || sheet.path("characterName").asText().isBlank()
                    || sheet.path("characterState").isMissingNode()) {
                throw new RuntimeCharacterSheetReadException("current character sheet response is incomplete");
            }
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeCharacterSheetReadException("current character sheet read interrupted", exception);
        } catch (IOException exception) {
            throw new RuntimeCharacterSheetReadException("current character sheet read failed", exception);
        }
    }
}
