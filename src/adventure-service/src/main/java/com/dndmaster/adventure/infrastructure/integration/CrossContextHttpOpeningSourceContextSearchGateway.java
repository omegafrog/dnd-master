package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.runtime.OpeningSourceContextSearchPort;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Calls story-source retrieval with an opening-specific question. */
public final class CrossContextHttpOpeningSourceContextSearchGateway implements OpeningSourceContextSearchPort {
    private static final String OPENING_QUERY =
            "Find the first playable opening situation: the earliest numbered location where the party arrives and can act. "
                    + "Return the immediate scene, location, why the party is there, the immediate problem, and visible things they can respond to. "
                    + "Exclude summary sections, later locations, hazards, monster statistics, "
                    + "combat rules, and damage procedures.";
    /** The story-source search already applies the opening-specific ordering; keep only its first result. */
    private static final int RESULT_LIMIT = 1;

    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper objectMapper;

    public CrossContextHttpOpeningSourceContextSearchGateway(
            HttpClient client, URI baseUri, Duration timeout, ObjectMapper objectMapper) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.baseUri = Objects.requireNonNull(baseUri, "base uri must not be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "object mapper must not be null");
    }

    @Override
    public List<Result> search(OwnerPlayerId ownerPlayerId, ScenarioPackage scenarioPackage) {
        Objects.requireNonNull(ownerPlayerId, "owner player id must not be null");
        Objects.requireNonNull(scenarioPackage, "scenario package must not be null");
        List<DocumentRequest> documents = scenarioPackage.documents().stream()
                .filter(document -> document.role() == ScenarioBundleDocumentRole.MAIN_SCENARIO)
                .filter(document -> "STORYBOOK".equalsIgnoreCase(document.documentType()))
                .map(document -> new DocumentRequest(document.knowledgeDocumentId().value(), document.extractionVersion()))
                .toList();
        if (documents.isEmpty()) return List.of();
        try {
            String body = objectMapper.writeValueAsString(new SearchRequest(
                    ownerPlayerId.value(), documents, List.of(), OPENING_QUERY, RESULT_LIMIT));
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("internal/v1/story-sources/search"))
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + ownerPlayerId.value())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("opening source search failed with status " + response.statusCode());
            }
            SearchResponse payload = objectMapper.readValue(response.body(), SearchResponse.class);
            if (payload.evidence() == null) return List.of();
            return payload.evidence().stream()
                    .filter(Objects::nonNull)
                    .map(item -> new Result(new KnowledgeDocumentId(item.knowledgeDocumentId()),
                            item.extractionVersion(), item.locator(), item.excerpt(), item.score()))
                    .toList();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("opening source search failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("opening source search interrupted", exception);
        }
    }

    record SearchRequest(UUID ownerId, List<DocumentRequest> documents, List<String> activeLocators,
                         String situation, int limit) {}
    record DocumentRequest(UUID documentId, long extractionVersion) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SearchResponse(List<Evidence> evidence) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Evidence(UUID knowledgeDocumentId, long extractionVersion, String locator, String excerpt, double score) {}
}
