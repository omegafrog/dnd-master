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
                            .toList(), request.situation(), request.thresholds(), request.tokenBudget()));
            HttpRequest httpRequest = HttpRequest.newBuilder(baseUri.resolve("internal/v1/character-context/search"))
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + request.ownerId())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new CharacterContextSearchPort.CharacterContextSearchException(
                        "character context search returned " + response.statusCode());
            }
            Response parsed = objectMapper.readValue(response.body(), Response.class);
            if (parsed.evidence() == null) return List.of();
            Set<String> requested = request.documents().stream()
                    .map(document -> key(document.documentId().value(), document.documentType(), document.extractionVersion()))
                    .collect(java.util.stream.Collectors.toSet());
            return parsed.evidence().stream().filter(Objects::nonNull)
                    .filter(item -> item.knowledgeDocumentId() != null && item.documentType() != null
                            && requested.contains(key(item.knowledgeDocumentId(), item.documentType(), item.extractionVersion())))
                    .map(item -> new Evidence(
                            new KnowledgeDocumentId(item.knowledgeDocumentId()), item.documentType(), item.extractionVersion(),
                            item.locator(), item.excerpt(), item.similarity())).toList();
        } catch (IOException exception) {
            throw new CharacterContextSearchPort.CharacterContextSearchException("character context search failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CharacterContextSearchPort.CharacterContextSearchException("character context search interrupted", exception);
        }
    }

    private static String key(UUID documentId, String documentType, long extractionVersion) {
        return documentId + ":" + documentType.toUpperCase(Locale.ROOT) + ":" + extractionVersion;
    }

    record WireRequest(UUID ownerId, List<WireDocument> documents, String situation,
                       Map<String, Double> thresholds, int tokenBudget) {}
    record WireDocument(UUID documentId, String documentType, long extractionVersion) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(UUID ownerId, List<WireEvidence> evidence) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record WireEvidence(UUID knowledgeDocumentId, String documentType, long extractionVersion,
                         String locator, String excerpt, double similarity) {}
}
