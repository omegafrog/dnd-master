package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentLookupPort;
import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class CrossContextHttpKnowledgeDocumentLookupGateway implements KnowledgeDocumentLookupPort {
    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper objectMapper;

    public CrossContextHttpKnowledgeDocumentLookupGateway(
            HttpClient client, URI baseUri, Duration timeout, ObjectMapper objectMapper) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.baseUri = Objects.requireNonNull(baseUri, "baseUri must not be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public List<KnowledgeDocumentRecord> findOwnedDocuments(UUID ownerPlayerId) {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("internal/v1/rulebooks?ownerId=" + ownerPlayerId))
                .timeout(timeout)
                .GET()
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new KnowledgeDocumentLookupException("cross-context lookup failed with status " + response.statusCode());
            }
            OwnedRulebooksResponse body = objectMapper.readValue(response.body(), OwnedRulebooksResponse.class);
            return body.rulebooks().stream()
                    .map(summary -> new KnowledgeDocumentRecord(
                            new KnowledgeDocumentId(summary.knowledgeDocumentId()),
                            KnowledgeDocumentStatus.valueOf(summary.status()),
                            summary.originalFilename(),
                            summary.documentType(),
                            summary.extractionVersion()))
                    .toList();
        } catch (IOException exception) {
            throw new KnowledgeDocumentLookupException("cross-context lookup failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new KnowledgeDocumentLookupException("cross-context lookup interrupted", exception);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OwnedRulebooksResponse(List<OwnedRulebookSummary> rulebooks) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OwnedRulebookSummary(
            java.util.UUID knowledgeDocumentId,
            String status,
            String documentType,
            String originalFilename,
            long extractionVersion) {}
}
