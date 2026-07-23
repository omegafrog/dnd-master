package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.scenario.compilation.ResolutionExtractionPort;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioSourceExcerptPort;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundle;
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

public final class CrossContextHttpScenarioSourceExcerptGateway implements ScenarioSourceExcerptPort {
    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper objectMapper;

    public CrossContextHttpScenarioSourceExcerptGateway(
            HttpClient client, URI baseUri, Duration timeout, ObjectMapper objectMapper) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.baseUri = Objects.requireNonNull(baseUri, "base uri must not be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "object mapper must not be null");
    }

    @Override
    public List<ResolutionExtractionPort.SourceExcerpt> load(ScenarioSourceBundle bundle) {
        try {
            String body = objectMapper.writeValueAsString(new ExcerptRequest(
                    bundle.id().value(), bundle.currentRevision().revision(),
                    bundle.currentRevision().documents().stream()
                            .map(document -> new DocumentRequest(document.knowledgeDocumentId().value(), document.extractionVersion()))
                            .toList()));
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("internal/v1/knowledge/source-excerpts"))
                    .timeout(timeout).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResolutionExtractionException("source excerpt lookup failed with status " + response.statusCode());
            }
            ExcerptResponse extracted = objectMapper.readValue(response.body(), ExcerptResponse.class);
            if (extracted.excerpts() == null) return List.of();
            return extracted.excerpts().stream().filter(Objects::nonNull)
                    .map(excerpt -> new ResolutionExtractionPort.SourceExcerpt(
                            new KnowledgeDocumentId(excerpt.documentId()), excerpt.extractionVersion(),
                            excerpt.locator(), excerpt.text())).toList();
        } catch (IOException exception) {
            throw new ResolutionExtractionException("source excerpt lookup failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResolutionExtractionException("source excerpt lookup interrupted", exception);
        }
    }

    record ExcerptRequest(java.util.UUID bundleId, long bundleRevision, List<DocumentRequest> documents) {}
    record DocumentRequest(java.util.UUID documentId, long extractionVersion) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ExcerptResponse(List<Excerpt> excerpts) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Excerpt(java.util.UUID documentId, long extractionVersion, String locator, String text) {}
}
