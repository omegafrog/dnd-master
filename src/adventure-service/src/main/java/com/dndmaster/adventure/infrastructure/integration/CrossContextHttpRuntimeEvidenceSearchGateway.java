package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.runtime.RuntimeEvidence;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceSearchPort;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceSearchRequest;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceType;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class CrossContextHttpRuntimeEvidenceSearchGateway implements RuntimeEvidenceSearchPort {
    private final HttpClient httpClient;
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper objectMapper;

    public CrossContextHttpRuntimeEvidenceSearchGateway(
            HttpClient httpClient, URI baseUri, Duration timeout, ObjectMapper objectMapper) {
        this.httpClient = Objects.requireNonNull(httpClient, "http client must not be null");
        this.baseUri = Objects.requireNonNull(baseUri, "base uri must not be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "object mapper must not be null");
    }

    @Override
    public List<RuntimeEvidence> search(RuntimeEvidenceSearchRequest request) {
        try {
            if (request.evidenceType() == RuntimeEvidenceType.RULEBOOK) {
                RuleSearchResponse response = post("internal/v1/rule-evidence/search",
                    new RuleSearchRequest(request.ownerPlayerId().value(), request.knowledgeDocumentIds(),
                                request.action(), "RULE", request.limit()), request.ownerPlayerId().value(), RuleSearchResponse.class);
                return response.evidence().stream()
                        .map(item -> new RuntimeEvidence(RuntimeEvidenceType.RULEBOOK,
                                new KnowledgeDocumentId(item.rulebookId()), 1L, item.locator(), item.excerpt()))
                        .toList();
            }
            StorySearchResponse response = post("internal/v1/story-sources/search",
                    new StorySearchRequest(request.ownerPlayerId().value(), request.knowledgeDocumentIds().stream()
                            .map(id -> new StoryDocument(id, extractionVersion(request, id))).toList(),
                            activeLocators(request), request.action(), request.limit()),
                    request.ownerPlayerId().value(), StorySearchResponse.class);
            return response.evidence().stream()
                    .map(item -> new RuntimeEvidence(RuntimeEvidenceType.STORYBOOK,
                            new KnowledgeDocumentId(item.knowledgeDocumentId()), item.extractionVersion(), item.locator(), item.excerpt()))
                    .toList();
        } catch (Exception exception) {
            throw new IllegalStateException("runtime evidence search failed", exception);
        }
    }

    private static long extractionVersion(RuntimeEvidenceSearchRequest request, UUID documentId) {
        return request.activeSourceContext() != null
                && request.activeSourceContext().knowledgeDocumentId().value().equals(documentId)
                ? request.activeSourceContext().extractionVersion() : 1L;
    }

    private static List<String> activeLocators(RuntimeEvidenceSearchRequest request) {
        return request.activeSourceContext() == null ? List.of() : List.of(request.activeSourceContext().locator());
    }

    private <T> T post(String path, Object payload, UUID ownerId, Class<T> responseType) throws Exception {
        String body = objectMapper.writeValueAsString(payload);
        HttpRequest httpRequest = HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(timeout)
                .header("Authorization", "Bearer " + ownerId)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) throw new IllegalStateException("runtime evidence search returned " + response.statusCode());
        return objectMapper.readValue(response.body(), responseType);
    }

    record RuleSearchRequest(UUID ownerId, List<UUID> rulebookIds, String situation, String queryIntent, int limit) {}
    record StorySearchRequest(UUID ownerId, List<StoryDocument> documents, List<String> activeLocators, String situation, int limit) {}
    record StoryDocument(UUID documentId, long extractionVersion) {}
    record RuleSearchResponse(UUID ownerId, List<RuleEvidenceItem> evidence) {}
    record RuleEvidenceItem(UUID rulebookId, UUID chunkId, String locator, String excerpt, double score, String chapter, String section) {}
    record StorySearchResponse(UUID ownerId, List<StoryEvidenceItem> evidence) {}
    record StoryEvidenceItem(UUID knowledgeDocumentId, long extractionVersion, String locator, String excerpt, double score) {}
}
