package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.scenario.compilation.ResolutionExtractionPort;
import com.dndmaster.adventure.domain.scenario.PublishedEvidenceProvenance;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioSourceExcerptPort;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
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
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(CrossContextHttpScenarioSourceExcerptGateway.class);
    private static final int MAX_EXCERPTS_FOR_RESOLUTION_EXTRACTION = 12;
    private static final int MAX_EXCERPTS_FOR_BLUEPRINT_EXTRACTION = 12;
    private static final String RULE_QUERY =
            "Search this rules source for rules relevant to the adventure, including ability checks, saving throws, difficulty classes, attacks, damage, conditions, and encounter procedures. Return the source passages themselves.";

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
        List<DocumentRequest> storybookDocuments = bundle.currentRevision().documents().stream()
                    .filter(document -> "STORYBOOK".equalsIgnoreCase(document.documentType()))
                    .filter(document -> document.role() != ScenarioBundleDocumentRole.MAP)
                    .map(document -> new DocumentRequest(document.knowledgeDocumentId().value(), document.extractionVersion()))
                .toList();
        List<DocumentRequest> rulebookDocuments = bundle.currentRevision().documents().stream()
                    .filter(document -> "RULEBOOK".equalsIgnoreCase(document.documentType()))
                    .map(document -> new DocumentRequest(document.knowledgeDocumentId().value(), document.extractionVersion()))
                .toList();
        List<com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection> mapDocuments = bundle.currentRevision().documents().stream()
                    .filter(document -> document.role() == ScenarioBundleDocumentRole.MAP)
                    .toList();
        devLog("dev_scenario_source_route outcome=selected bundleId={} expected=map_assets_without_text_search actualStorybookSearchDocumentCount={} actualRulebookSearchDocumentCount={} mapDocumentCount={} mapTextSearchSkippedCount={} mapRoute=source_preview_assets",
                bundle.id().value(), storybookDocuments.size(), rulebookDocuments.size(), mapDocuments.size(), mapDocuments.size());
        List<ResolutionExtractionPort.SourceExcerpt> scenarioExcerpts = searchPreparationSources(
                bundle, storybookDocuments, "STORYBOOK", RULE_QUERY,
                MAX_EXCERPTS_FOR_BLUEPRINT_EXTRACTION);
        List<ResolutionExtractionPort.SourceExcerpt> rulebookExcerpts = searchPreparationSources(
                bundle, rulebookDocuments, "RULEBOOK", RULE_QUERY,
                MAX_EXCERPTS_FOR_RESOLUTION_EXTRACTION);
        if (!storybookDocuments.isEmpty() && scenarioExcerpts.isEmpty()) {
                throw new ResolutionExtractionException("published storybook evidence is unavailable");
            }
        if (!rulebookDocuments.isEmpty() && rulebookExcerpts.isEmpty()) {
                throw new ResolutionExtractionException("published rulebook evidence is unavailable");
            }
        List<ResolutionExtractionPort.SourceExcerpt> mapAssets = mapDocuments.stream()
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
                    List.of(), situation, limit));
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("internal/v1/evidence-candidates/preparation-search"))
                    .timeout(timeout).header("Content-Type", "application/json")
                    .header("X-Internal-Token", internalToken)
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                devLog("dev_preparation_search outcome=http_error bundleId={} documentType={} queryFingerprint={} status={} response={}",
                        bundle.id().value(), documentType,
                        com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fingerprint(situation),
                        response.statusCode(), com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.safeBody(response.body()));
                throw new ResolutionExtractionException("source excerpt lookup failed with status " + response.statusCode());
            }
            PreparationSearchResponse extracted = objectMapper.readValue(response.body(), PreparationSearchResponse.class);
            if (!bundle.ownerPlayerId().value().equals(extracted.ownerId()) || !bundle.id().value().equals(extracted.scenarioSourceBundleId())
                    || extracted.candidates() == null) {
                throw new ResolutionExtractionException("preparation evidence response scope does not match its request");
            }
            List<ResolutionExtractionPort.SourceExcerpt> selected = extracted.candidates().stream()
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
            devLog("dev_preparation_search outcome=success bundleId={} documentType={} queryFingerprint={} scopeCount={} limit={} candidateCount={} candidates={}",
                    bundle.id().value(), documentType,
                    com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fingerprint(situation),
                    documents.size(), limit, selected.size(), selected.stream().map(item -> item.documentId().value()
                            + ":" + item.extractionVersion() + ":" + item.locator()).toList());
            return selected;
        } catch (IOException exception) {
            throw new ResolutionExtractionException("source excerpt lookup failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResolutionExtractionException("source excerpt lookup interrupted", exception);
        }
    }

    private static void devLog(String pattern, Object... args) {
        if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) LOGGER.info(pattern, args);
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
        long startedAt = System.nanoTime();
        devLog("dev_scenario_map_assets outcome=started documentId={} extractionVersion={} expected=source_preview_assets actual=loading",
                document.knowledgeDocumentId().value(), document.extractionVersion());
        try {
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(
                            "api/v1/rulebooks/" + document.knowledgeDocumentId().value() + "/source-preview"))
                    .timeout(timeout).header("X-Internal-Token", internalToken).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                devLog("dev_scenario_map_assets outcome=failed documentId={} extractionVersion={} expectedStatus=2xx actualStatus={} failureClass=HttpStatus recoveryAction=reject_compilation elapsedMs={}",
                        document.knowledgeDocumentId().value(), document.extractionVersion(), response.statusCode(),
                        java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
                throw new ResolutionExtractionException("map source preview lookup failed with status " + response.statusCode());
            }
            SourcePreviewResponse preview = objectMapper.readValue(response.body(), SourcePreviewResponse.class);
            List<PreviewAsset> assets = preview == null || preview.assets() == null ? List.of() : preview.assets().stream()
                    .filter(Objects::nonNull).toList();
            boolean pdf = document.originalFilename().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf");
            List<String> imageLocators = pdf
                    ? assets.stream().map(PreviewAsset::pageNumber).filter(Objects::nonNull).distinct()
                            .map(page -> "page " + page).toList()
                    : assets.stream().map(PreviewAsset::locator).toList();
            List<ResolutionExtractionPort.SourceExcerpt> excerpts = imageLocators.stream().map(locator ->
                    new ResolutionExtractionPort.SourceExcerpt("MAP", document.knowledgeDocumentId(), document.extractionVersion(),
                            "asset:" + locator, "MAP asset=\"" + locator
                                    + "\" image=\"" + locator + "\" confidence=0.9 safety=SAFE"))
                    .toList();
            devLog("dev_scenario_map_assets outcome={} documentId={} extractionVersion={} expectedImageLocatorCount=at_least_1 actualImageLocatorCount={} previewAssetCount={} imageSource={} locators={} elapsedMs={} nextAction={}",
                    imageLocators.isEmpty() ? "empty" : "completed", document.knowledgeDocumentId().value(), document.extractionVersion(),
                    imageLocators.size(), assets.size(), pdf ? "rendered_pdf_page" : "source_image_asset", imageLocators,
                    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
                    imageLocators.isEmpty() ? "continue_compilation_without_map_definition" : "compile_map_image_assets");
            return excerpts;
        } catch (IOException exception) {
            devLog("dev_scenario_map_assets outcome=failed documentId={} extractionVersion={} expected=source_preview_assets actual=failure failureClass={} recoveryAction=reject_compilation elapsedMs={}",
                    document.knowledgeDocumentId().value(), document.extractionVersion(), exception.getClass().getSimpleName(),
                    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
            throw new ResolutionExtractionException("map source preview lookup failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            devLog("dev_scenario_map_assets outcome=failed documentId={} extractionVersion={} expected=source_preview_assets actual=interrupted failureClass=InterruptedException recoveryAction=abort_compilation elapsedMs={}",
                    document.knowledgeDocumentId().value(), document.extractionVersion(),
                    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
            throw new ResolutionExtractionException("map source preview lookup interrupted", exception);
        }
    }

    record PreparationSearchRequest(UUID ownerId, UUID scenarioSourceBundleId, List<PreparationScope> scope,
                                    List<String> activeLocators, String query, int denseLimit) {}
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
