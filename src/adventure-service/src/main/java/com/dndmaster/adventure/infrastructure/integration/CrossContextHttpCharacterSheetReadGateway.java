package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.runtime.CharacterSheetReadPort;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

public final class CrossContextHttpCharacterSheetReadGateway implements CharacterSheetReadPort {
    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper objectMapper;

    public CrossContextHttpCharacterSheetReadGateway(HttpClient client, URI baseUri, Duration timeout, ObjectMapper objectMapper) {
        this.client = Objects.requireNonNull(client);
        this.baseUri = Objects.requireNonNull(baseUri);
        this.timeout = Objects.requireNonNull(timeout);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public CharacterSheet read(CharacterSheetId characterSheetId) {
        try {
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("internal/v1/character-sheets/" + characterSheetId.value()))
                    .timeout(timeout).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("character sheet read failed with status " + response.statusCode());
            }
            SheetResponse sheet = objectMapper.readValue(response.body(), SheetResponse.class);
            return new CharacterSheet(characterSheetId, sheet.characterName(), sheet.level(), sheet.version());
        } catch (IOException exception) {
            throw new IllegalStateException("character sheet read failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("character sheet read interrupted", exception);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SheetResponse(String characterName, int level, long version) {}
}
