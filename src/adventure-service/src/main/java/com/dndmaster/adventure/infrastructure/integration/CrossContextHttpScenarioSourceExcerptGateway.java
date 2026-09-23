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
    private static final String RESOLUTION_SOURCE_QUERY =
            "Find story passages that describe how player actions are resolved, including checks, thresholds, consequences, and damage.";

    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper objectMapper;
    private final String internalToken;

    public CrossContextHttpScenarioSourceExcerptGateway(
            HttpClient client, URI baseUri, Duration timeout, ObjectMapper objectMapper) {
        this(client, baseUri, timeout, objectMapper, "");
    }

    public CrossContextHttpScenarioSourceExcerptGateway(
            HttpClient client, URI baseUri, Duration timeout, ObjectMapper objectMapper, String internalToken) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.baseUri = Objects.requireNonNull(baseUri, "base uri must not be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "object mapper must not be null");
        if (internalToken == null || internalToken.isBlank()) {
            throw new IllegalArgumentException("internal token must not be blank");
        }
        this.internalToken = internalToken;
    }

    @Override
    public List<ResolutionExtractionPort.SourceExcerpt> load(ScenarioSourceBundle bundle) {
        List<DocumentRequest> storybookDocuments = new java.util.ArrayList<>(bundle.currentRevision().documents().stream()
                    .filter(document -> "STORYBOOK".equalsIgnoreCase(document.documentType()))
                    .map(document -> new DocumentRequest(document.knowledgeDocumentId().value(), document.extractionVersion()))
                .toList());
        List<DocumentRequest> rulebookDocuments = bundle.currentRevision().documents().stream()
                    .filter(document -> "RULEBOOK".equalsIgnoreCase(document.documentType()))
                    .map(document -> new DocumentRequest(document.knowledgeDocumentId().value(), document.extractionVersion()))
                .toList();
        List<ResolutionExtractionPort.SourceExcerpt> rulebookExcerpts = searchPreparationSources(
                bundle, rulebookDocuments, "RULEBOOK", "Extract source-grounded rule procedures.",
                MAX_EXCERPTS_FOR_RESOLUTION_EXTRACTION);
        List<ResolutionExtractionPort.SourceExcerpt> scenarioExcerpts = searchPreparationSources(
                bundle, storybookDocuments, "STORYBOOK", RESOLUTION_SOURCE_QUERY,
                MAX_EXCERPTS_FOR_BLUEPRINT_EXTRACTION);
        if (!storybookDocuments.isEmpty() && scenarioExcerpts.isEmpty()) {
                throw new ResolutionExtractionException("published storybook evidence is unavailable");
            }
        if (!rulebookDocuments.isEmpty() && rulebookExcerpts.isEmpty()) {
                throw new ResolutionExtractionException("published rulebook evidence is unavailable");
            }
        List<ResolutionExtractionPort.SourceExcerpt> mapAssets = bundle.currentRevision().documents().stream()
                    .filter(document -> document.role() == com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole.MAP)
                    .flatMap(document -> loadMapAssets(document).stream())
                    .toList();
        return java.util.stream.Stream.of(scenarioExcerpts, rulebookExcerpts, mapAssets)
                    .flatMap(List::stream).toList();
    }

    private List<ResolutionExtractionPort.SourceExcerpt> searchPreparationSources(
            ScenarioSourceBundle bundle, List<DocumentRequest> documents, String documentType, String situation, int limit) {
        if (documents.isEmpty()) return List.of();
        try {
            String body = objectMapper.writeValueAsString(new PreparationSearchRequest(
                    bundle.ownerPlayerId().value(), bundle.id().value(), documents.stream()
                    .map(document -> new PreparationScope(document.documentId(), document.extractionVersion(), documentType)).toList(),
                    List.of(), situation, limit, limit));
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("internal/v1/evidence-candidates/preparation-search"))
                    .timeout(timeout).header("Content-Type", "application/json")
                    .header("X-Internal-Token", internalToken)
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResolutionExtractionException("source excerpt lookup failed with status " + response.statusCode());
            }
            PreparationSearchResponse extracted = objectMapper.readValue(response.body(), PreparationSearchResponse.class);
            if (!bundle.ownerPlayerId().value().equals(extracted.ownerId()) || !bundle.id().value().equals(extracted.scenarioSourceBundleId())
                    || extracted.candidates() == null) {
                throw new ResolutionExtractionException("preparation evidence response scope does not match its request");
            }
            return extracted.candidates().stream()
                    .filter(Objects::nonNull)
                    .limit(limit)
                    .map(candidate -> {
                        if (!documentType.equals(candidate.documentType()) || documents.stream().noneMatch(document ->
                                document.documentId().equals(candidate.documentId())
                                        && document.extractionVersion() == candidate.extractionVersion())) {
                            throw new ResolutionExtractionException("preparation evidence is outside the selected document scope");
                        }
                        return new ResolutionExtractionPort.SourceExcerpt(documentType, toProvenance(candidate.documentId(),
                                candidate.extractionVersion(), candidate.locator(), candidate.provenance()), candidate.excerpt());
                    }).toList();
        } catch (IOException exception) {
            throw new ResolutionExtractionException("source excerpt lookup failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResolutionExtractionException("source excerpt lookup interrupted", exception);
        }
    }

    private static PublishedEvidenceProvenance toProvenance(
            UUID documentId, long extractionVersion, String locator, ProvenanceResponse provenance) {
        if (provenance == null) {
            throw new ResolutionExtractionException("published evidence is missing provenance");
        }
        if (!locator.equals(provenance.originalLocator())) {
            throw new ResolutionExtractionException("published evidence provenance does not match its result");
        }
        return new PublishedEvidenceProvenance(
                new KnowledgeDocumentId(documentId), extractionVersion, provenance.pageNumber(),
                provenance.sectionPath(), provenance.bbox(), provenance.tableCell(), provenance.originalLocator());
    }

    private List<ResolutionExtractionPort.SourceExcerpt> loadMapAssets(
            com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection document) {
        try {
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(
                            "api/v1/rulebooks/" + document.knowledgeDocumentId().value() + "/source-preview"))
                    .timeout(timeout).header("X-Internal-Token", internalToken).GET().build();
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

    record PreparationSearchRequest(UUID ownerId, UUID scenarioSourceBundleId, List<PreparationScope> scope,
                                    List<String> activeLocators, String query, int denseLimit, int bm25Limit) {}
    record PreparationScope(UUID documentId, long extractionVersion, String documentType) {}
    record DocumentRequest(java.util.UUID documentId, long extractionVersion) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record PreparationSearchResponse(UUID ownerId, UUID scenarioSourceBundleId, List<PreparationCandidate> candidates) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record PreparationCandidate(UUID chunkId, UUID documentId, long extractionVersion, String documentType,
            String locator, String excerpt, ProvenanceResponse provenance) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProvenanceResponse(int pageNumber, List<String> sectionPath, List<Double> bbox, String tableCell, String originalLocator) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record SourcePreviewResponse(String content, List<PreviewAsset> assets) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record PreviewAsset(String kind, String locator, String contentType, Integer pageNumber) {}
}
