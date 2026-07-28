package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.scenario.compilation.CharacterContextSearchPort;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

public final class CrossContextHttpCharacterContextSearchGateway implements CharacterContextSearchPort {
    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper objectMapper;

    public CrossContextHttpCharacterContextSearchGateway(
            HttpClient client, URI baseUri, Duration timeout, ObjectMapper objectMapper) {
        this.client = Objects.requireNonNull(client);
        this.baseUri = Objects.requireNonNull(baseUri);
        this.timeout = Objects.requireNonNull(timeout);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public List<Evidence> search(Request request) {
        try {
            String body = objectMapper.writeValueAsString(new WireRequest(
                    request.ownerId(), request.documents().stream()
                            .map(document -> new WireDocument(document.documentId().value(), document.documentType(), document.extractionVersion()))
                            .toList(), request.situation(), request.tokenBudget()));
            HttpRequest httpRequest = HttpRequest.newBuilder(baseUri.resolve("internal/v1/character-context/search"))
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + request.ownerId())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) throw new IllegalStateException("character context search returned " + response.statusCode());
            Response parsed = objectMapper.readValue(response.body(), Response.class);
            if (parsed.evidence() == null) return List.of();
            return parsed.evidence().stream().filter(Objects::nonNull).map(item -> new Evidence(
                    new KnowledgeDocumentId(item.knowledgeDocumentId()), item.documentType(), item.extractionVersion(),
                    item.locator(), item.excerpt(), item.similarity())).toList();
        } catch (IOException exception) {
            throw new IllegalStateException("character context search failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("character context search interrupted", exception);
        }
    }

    record WireRequest(UUID ownerId, List<WireDocument> documents, String situation, int tokenBudget) {}
    record WireDocument(UUID documentId, String documentType, long extractionVersion) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(UUID ownerId, List<WireEvidence> evidence) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record WireEvidence(UUID knowledgeDocumentId, String documentType, long extractionVersion,
                         String locator, String excerpt, double similarity) {}
}
