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
    private static final int MAX_EXCERPTS_FOR_RESOLUTION_EXTRACTION = 3;
    private static final int MAX_EXCERPTS_FOR_BLUEPRINT_EXTRACTION = 12;
    private static final int MAX_EXCERPT_CHARACTERS = 900;

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
                    .map(document -> new DocumentRequest(document.knowledgeDocumentId().value(), document.extractionVersion()))
                    .toList());
            List<OwnedRulebookDocument> rulebooks = loadOwnedRulebooks(bundle.ownerPlayerId().value());
            List<ResolutionExtractionPort.SourceExcerpt> rulebookExcerpts = rulebooks.stream()
                    .filter(document -> "RULEBOOK".equalsIgnoreCase(document.documentType())
                            && "INDEXED".equalsIgnoreCase(document.status()))
                    .flatMap(document -> loadRulebookExcerpts(document).stream())
                    .toList();
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
            return java.util.stream.Stream.concat(scenarioExcerpts.stream(), rulebookExcerpts.stream()).toList();
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

    private static String abbreviate(String excerpt) {
        if (excerpt == null || excerpt.length() <= MAX_EXCERPT_CHARACTERS) return excerpt;
        int anchor = firstRuleAnchor(excerpt);
        int start = Math.max(0, Math.min(anchor - MAX_EXCERPT_CHARACTERS / 2,
                excerpt.length() - MAX_EXCERPT_CHARACTERS));
        return "…" + excerpt.substring(start, start + MAX_EXCERPT_CHARACTERS) + "…";
    }

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
    record OwnedRulebookDocument(
            java.util.UUID knowledgeDocumentId, String documentType, String status, long extractionVersion) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record SourcePreviewResponse(String content) {}
}
