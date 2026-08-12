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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CrossContextHttpScenarioSourceExcerptGateway implements ScenarioSourceExcerptPort {
    private static final int MAX_EXCERPTS_FOR_RESOLUTION_EXTRACTION = 12;
    private static final int MAX_EXCERPTS_FOR_BLUEPRINT_EXTRACTION = 12;
    private static final int MAX_EXCERPT_CHARACTERS = 900;
    private static final Pattern RESOLUTION_ANCHOR = Pattern.compile(
            "(?is)\\b(?:dc\\s*\\d+\\s+[a-z]+(?:\\s*\\([^)]*\\))?\\s+"
                    + "(?:sa\\s*ving\\s+throw(?:s)?|check(?:s)?)|"
                    + "(?:saving\\s+throw(?:s)?|attack(?:s)?|damage(?:s)?|recharge|roll(?:s)?))\\b");

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
            // The story-source endpoint authorizes STORYBOOK documents only. A scenario
            // bundle also contains shared catalog rulebooks, which are handled separately
            // below via source previews; sending them in the mixed request causes a 403.
            List<DocumentRequest> documents = new java.util.ArrayList<>(bundle.currentRevision().documents().stream()
                    .filter(document -> "STORYBOOK".equalsIgnoreCase(document.documentType()))
                    .map(document -> new DocumentRequest(document.knowledgeDocumentId().value(), document.extractionVersion()))
                    .toList());
            List<OwnedRulebookDocument> rulebooks = new ArrayList<>(loadOwnedRulebooks(bundle.ownerPlayerId().value()));
            rulebooks.addAll(loadCatalogRulebooks());
            List<ResolutionExtractionPort.SourceExcerpt> rulebookExcerpts = rulebooks.stream()
                    .filter(document -> "RULEBOOK".equalsIgnoreCase(document.documentType())
                            && "INDEXED".equalsIgnoreCase(document.status()))
                    .flatMap(document -> loadRulebookExcerpts(document).stream())
                    .collect(java.util.stream.Collectors.collectingAndThen(
                            java.util.stream.Collectors.toMap(document -> document.documentId(), document -> document, (left, right) -> left),
                            values -> new ArrayList<>(values.values())));
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
                            new KnowledgeDocumentId(excerpt.knowledgeDocumentId()), excerpt.extractionVersion(),
                            excerpt.locator(), abbreviate(excerpt.excerpt()))).toList();
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

    private List<OwnedRulebookDocument> loadOwnedRulebooks(java.util.UUID ownerId)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(
                        baseUri.resolve("internal/v1/rulebooks?ownerId=" + ownerId))
                .timeout(timeout)
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ResolutionExtractionException(
                    "owned rulebook lookup failed with status " + response.statusCode());
        }
        OwnedRulebooksResponse result = objectMapper.readValue(response.body(), OwnedRulebooksResponse.class);
        return result.rulebooks() == null ? List.of() : result.rulebooks();
    }

    private List<OwnedRulebookDocument> loadCatalogRulebooks() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("api/v1/rulebook-catalog"))
                .timeout(timeout).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) return List.of();
        CatalogRulebooksResponse result = objectMapper.readValue(response.body(), CatalogRulebooksResponse.class);
        if (result.rulebooks() == null) return List.of();
        return result.rulebooks().stream()
                .filter(item -> "READY".equalsIgnoreCase(item.status()) && item.rulebookId() != null)
                .map(item -> new OwnedRulebookDocument(java.util.UUID.fromString(item.rulebookId()), "RULEBOOK", "INDEXED", item.extractionVersion()))
                .toList();
    }

    private List<ResolutionExtractionPort.SourceExcerpt> loadRulebookExcerpts(OwnedRulebookDocument document) {
        try {
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(
                            "api/v1/rulebooks/" + document.knowledgeDocumentId() + "/source-preview"))
                    .timeout(timeout).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResolutionExtractionException(
                        "rulebook source preview lookup failed with status " + response.statusCode());
            }
            SourcePreviewResponse preview = objectMapper.readValue(response.body(), SourcePreviewResponse.class);
            String content = preview.content() == null ? "" : preview.content();
            List<ResolutionExtractionPort.SourceExcerpt> excerpts = new java.util.ArrayList<>();
            for (int start = 0; start < content.length(); start += MAX_EXCERPT_CHARACTERS) {
                int end = Math.min(content.length(), start + MAX_EXCERPT_CHARACTERS);
                excerpts.add(new ResolutionExtractionPort.SourceExcerpt(
                        new KnowledgeDocumentId(document.knowledgeDocumentId()), document.extractionVersion(),
                        "document:offset:" + start + "-" + end, content.substring(start, end)));
            }
            return excerpts;
        } catch (IOException exception) {
            throw new ResolutionExtractionException("rulebook source preview lookup failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResolutionExtractionException("rulebook source preview lookup interrupted", exception);
        }
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
                    new ResolutionExtractionPort.SourceExcerpt(document.knowledgeDocumentId(), document.extractionVersion(),
                            "asset:" + asset.locator(), "MAP asset=" + asset.locator()
                                    + " image=" + asset.locator() + " confidence=0.9 safety=SAFE"))
                    .toList();
        } catch (IOException exception) {
            throw new ResolutionExtractionException("map source preview lookup failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResolutionExtractionException("map source preview lookup interrupted", exception);
        }
    }

    static String abbreviate(String excerpt) {
        if (excerpt == null || excerpt.length() <= MAX_EXCERPT_CHARACTERS) return excerpt;
        String resolutionWindow = resolutionWindow(excerpt);
        if (resolutionWindow != null) return resolutionWindow;
        int anchor = firstRuleAnchor(excerpt);
        int start = Math.max(0, Math.min(anchor - MAX_EXCERPT_CHARACTERS / 2,
                excerpt.length() - MAX_EXCERPT_CHARACTERS));
        return "…" + excerpt.substring(start, start + MAX_EXCERPT_CHARACTERS) + "…";
    }

    private static String resolutionWindow(String excerpt) {
        Matcher matcher = RESOLUTION_ANCHOR.matcher(excerpt);
        List<AnchorWindow> windows = new ArrayList<>();
        while (matcher.find()) {
            windows.add(new AnchorWindow(
                    matcher.start(), matcher.end(), matcher.group().toLowerCase(java.util.Locale.ROOT).matches(".*\\bdc\\s*\\d+.*"),
                    matcher.group().toLowerCase(java.util.Locale.ROOT).contains("saving")));
        }
        windows.sort(Comparator.comparing(AnchorWindow::explicitDc).reversed()
                .thenComparing(Comparator.comparing(AnchorWindow::savingThrow).reversed())
                .thenComparingInt(AnchorWindow::start));
        StringBuilder result = new StringBuilder();
        for (AnchorWindow window : windows.stream().limit(2).toList()) {
            int start = Math.max(0, window.start() - 140);
            int end = Math.min(excerpt.length(), window.end() + 260);
            if (result.length() > 0) result.append("\n…\n");
            result.append(excerpt, start, end);
        }
        return result.length() == 0 ? null
                : result.substring(0, Math.min(result.length(), MAX_EXCERPT_CHARACTERS));
    }

    private record AnchorWindow(int start, int end, boolean explicitDc, boolean savingThrow) {}

    private static int firstRuleAnchor(String excerpt) {
        String lower = excerpt.toLowerCase(java.util.Locale.ROOT);
        int anchor = Integer.MAX_VALUE;
        for (String marker : List.of("check", "saving throw", "dc", "roll", "perception")) {
            int index = lower.indexOf(marker);
            if (index >= 0) anchor = Math.min(anchor, index);
        }
        return anchor == Integer.MAX_VALUE ? 0 : anchor;
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
    record Excerpt(java.util.UUID knowledgeDocumentId, long extractionVersion, String locator, String excerpt) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record OwnedRulebooksResponse(List<OwnedRulebookDocument> rulebooks) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record CatalogRulebooksResponse(List<CatalogRulebookDocument> rulebooks) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record CatalogRulebookDocument(String rulebookId, String status, long extractionVersion) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record OwnedRulebookDocument(
            java.util.UUID knowledgeDocumentId, String documentType, String status, long extractionVersion) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record SourcePreviewResponse(String content, List<PreviewAsset> assets) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record PreviewAsset(String kind, String locator, String contentType, Integer pageNumber) {}
}
