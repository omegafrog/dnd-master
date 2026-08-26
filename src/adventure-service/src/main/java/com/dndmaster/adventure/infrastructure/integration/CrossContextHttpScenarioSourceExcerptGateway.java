package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.scenario.compilation.ResolutionExtractionPort;
import com.dndmaster.adventure.domain.scenario.PublishedEvidenceProvenance;
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
import java.util.ArrayList;
import java.util.UUID;

public final class CrossContextHttpScenarioSourceExcerptGateway implements ScenarioSourceExcerptPort {
    private static final int MAX_EXCERPTS_FOR_RESOLUTION_EXTRACTION = 12;
    private static final int MAX_EXCERPTS_FOR_BLUEPRINT_EXTRACTION = 12;

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
            List<DocumentRequest> documents = new java.util.ArrayList<>(bundle.currentRevision().documents().stream()
                    .filter(document -> "STORYBOOK".equalsIgnoreCase(document.documentType()))
                    .map(document -> new DocumentRequest(document.knowledgeDocumentId().value(), document.extractionVersion()))
                    .toList());
            List<UUID> rulebookIds = bundle.currentRevision().documents().stream()
                    .filter(document -> "RULEBOOK".equalsIgnoreCase(document.documentType()))
                    .map(document -> document.knowledgeDocumentId().value())
                    .toList();
            List<ResolutionExtractionPort.SourceExcerpt> rulebookExcerpts = loadRulebookEvidence(
                    bundle.ownerPlayerId().value(), rulebookIds);
            String body = objectMapper.writeValueAsString(new ExcerptRequest(
                    bundle.ownerPlayerId().value(),
                    documents));
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("internal/v1/story-sources/search"))
                    .timeout(timeout).header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + bundle.ownerPlayerId().value())
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResolutionExtractionException("source excerpt lookup failed with status " + response.statusCode());
            }
            StorySourceSearchResponse extracted = objectMapper.readValue(response.body(), StorySourceSearchResponse.class);
            if (extracted.evidence() == null) return List.of();
            List<ResolutionExtractionPort.SourceExcerpt> scenarioExcerpts = extracted.evidence().stream()
                    .filter(Objects::nonNull)
                    .limit(MAX_EXCERPTS_FOR_BLUEPRINT_EXTRACTION)
                    .map(excerpt -> new ResolutionExtractionPort.SourceExcerpt(
                            "STORYBOOK", toProvenance(excerpt.knowledgeDocumentId(), excerpt.extractionVersion(),
                                    excerpt.locator(), excerpt.provenance()), excerpt.excerpt())).toList();
            if (!documents.isEmpty() && scenarioExcerpts.isEmpty()) {
                throw new ResolutionExtractionException("published storybook evidence is unavailable");
            }
            if (!rulebookIds.isEmpty() && rulebookExcerpts.isEmpty()) {
                throw new ResolutionExtractionException("published rulebook evidence is unavailable");
            }
            List<ResolutionExtractionPort.SourceExcerpt> mapAssets = bundle.currentRevision().documents().stream()
                    .filter(document -> document.role() == com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole.MAP)
                    .flatMap(document -> loadMapAssets(document).stream())
                    .toList();
            return java.util.stream.Stream.of(scenarioExcerpts, rulebookExcerpts, mapAssets)
                    .flatMap(List::stream).toList();
        } catch (IOException exception) {
            throw new ResolutionExtractionException("source excerpt lookup failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResolutionExtractionException("source excerpt lookup interrupted", exception);
        }
    }

    private List<ResolutionExtractionPort.SourceExcerpt> loadRulebookEvidence(UUID ownerId, List<UUID> rulebookIds) {
        if (rulebookIds.isEmpty()) return List.of();
        try {
            String body = objectMapper.writeValueAsString(new RuleEvidenceRequest(
                    ownerId, rulebookIds, "Extract source-grounded rule procedures.", "MIXED",
                    MAX_EXCERPTS_FOR_RESOLUTION_EXTRACTION));
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("internal/v1/rule-evidence/search"))
                    .timeout(timeout).header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + ownerId)
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResolutionExtractionException(
                        "published rulebook evidence lookup failed with status " + response.statusCode());
            }
            RuleEvidenceResponse result = objectMapper.readValue(response.body(), RuleEvidenceResponse.class);
            return result.evidence() == null ? List.of() : result.evidence().stream()
                    .filter(Objects::nonNull)
                    .map(evidence -> new ResolutionExtractionPort.SourceExcerpt(
                            "RULEBOOK", toProvenance(evidence.rulebookId(), evidence.extractionVersion(),
                                    evidence.locator(), evidence.provenance()), evidence.excerpt()))
                    .toList();
        } catch (IOException exception) {
            throw new ResolutionExtractionException("published rulebook evidence lookup failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResolutionExtractionException("published rulebook evidence lookup interrupted", exception);
        }
    }

    private static PublishedEvidenceProvenance toProvenance(
            UUID documentId, long extractionVersion, String locator, ProvenanceResponse provenance) {
        if (provenance == null) {
            throw new ResolutionExtractionException("published evidence is missing provenance");
        }
        if (!documentId.equals(provenance.documentId()) || extractionVersion != provenance.extractionVersion()
                || !locator.equals(provenance.locator())) {
            throw new ResolutionExtractionException("published evidence provenance does not match its result");
        }
        return new PublishedEvidenceProvenance(
                new KnowledgeDocumentId(documentId), extractionVersion, provenance.pageNumber(),
                provenance.sectionPath(), provenance.bbox(), provenance.tableCell(), provenance.locator());
    }

    private List<ResolutionExtractionPort.SourceExcerpt> loadMapAssets(
            com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection document) {
        try {
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(
                            "api/v1/rulebooks/" + document.knowledgeDocumentId().value() + "/source-preview"))
                    .timeout(timeout).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResolutionExtractionException("map source preview lookup failed with status " + response.statusCode());
            }
            SourcePreviewResponse preview = objectMapper.readValue(response.body(), SourcePreviewResponse.class);
            if (preview.assets() == null) return List.of();
            return preview.assets().stream().filter(Objects::nonNull).map(asset ->
                    new ResolutionExtractionPort.SourceExcerpt(document.documentType(), document.knowledgeDocumentId(), document.extractionVersion(),
                            "asset:" + asset.locator(), "MAP asset=\"" + asset.locator()
                                    + "\" image=\"" + asset.locator() + "\" confidence=0.9 safety=SAFE"))
                    .toList();
        } catch (IOException exception) {
            throw new ResolutionExtractionException("map source preview lookup failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResolutionExtractionException("map source preview lookup interrupted", exception);
        }
    }

    record ExcerptRequest(java.util.UUID ownerId, List<DocumentRequest> documents,
                          List<String> activeLocators, String situation, Integer limit) {
        ExcerptRequest(java.util.UUID ownerId, List<DocumentRequest> documents) {
            this(ownerId, documents, List.of(), "Extract source-grounded resolution procedures.",
                    MAX_EXCERPTS_FOR_RESOLUTION_EXTRACTION);
        }
    }
    record DocumentRequest(java.util.UUID documentId, long extractionVersion) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record StorySourceSearchResponse(List<Excerpt> evidence) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Excerpt(java.util.UUID knowledgeDocumentId, long extractionVersion, String locator, String excerpt,
            ProvenanceResponse provenance) {}
    record RuleEvidenceRequest(UUID ownerId, List<UUID> rulebookIds, String situation, String queryIntent, int limit) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RuleEvidenceResponse(List<RuleEvidenceItem> evidence) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RuleEvidenceItem(UUID rulebookId, UUID chunkId, String locator, String excerpt,
            double score, ProvenanceResponse provenance) {
        long extractionVersion() { return provenance == null ? 0 : provenance.extractionVersion(); }
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProvenanceResponse(UUID documentId, long extractionVersion, int pageNumber, List<String> sectionPath,
            List<Double> bbox, String tableCell, String locator) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record SourcePreviewResponse(String content, List<PreviewAsset> assets) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record PreviewAsset(String kind, String locator, String contentType, Integer pageNumber) {}
}
