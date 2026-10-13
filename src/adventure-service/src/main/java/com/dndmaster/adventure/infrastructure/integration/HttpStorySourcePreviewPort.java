package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.scenario.preparation.StorySourcePreviewPort;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import com.dndmaster.adventure.domain.scenario.RequiredSituation;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Returns bounded RAG candidates from the selected story document. */
public final class HttpStorySourcePreviewPort implements StorySourcePreviewPort {
    private static final int STORY_CANDIDATE_LIMIT = 15;
    private static final int RULE_SEARCH_CANDIDATE_LIMIT = 8;
    private static final int RULE_EXCERPTS_PER_SITUATION = 4;
    private static final int MAX_RAG_EXCERPT_CODE_POINTS = 1200;
    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final String token;

    public HttpStorySourcePreviewPort(HttpClient client, URI baseUri, Duration timeout, ObjectMapper mapper, String token) {
        this.client = Objects.requireNonNull(client);
        this.baseUri = Objects.requireNonNull(baseUri);
        this.timeout = Objects.requireNonNull(timeout);
        this.mapper = Objects.requireNonNull(mapper);
        if (token == null || token.isBlank()) throw new IllegalArgumentException("internal token is required");
        this.token = token;
    }

    @Override public List<Passage> load(ScenarioBundleDocumentSelection document, UUID ownerId, UUID bundleId) {
        if (document.role() != com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole.MAIN_SCENARIO
                || !"STORYBOOK".equalsIgnoreCase(document.documentType())) throw new IllegalArgumentException("main story document is required");
        return searchBatch(ownerId, bundleId, List.of(document), List.of(
                "Find story-essential situations, explicit prerequisite relationships, intermediate events, and the ending."),
                STORY_CANDIDATE_LIMIT).getFirst().stream()
                .collect(java.util.stream.Collectors.toMap(Candidate::locator, candidate -> candidate, (first, ignored) -> first,
                        java.util.LinkedHashMap::new))
                .values().stream()
                .map(candidate -> new Passage(new ScenarioSourceReference(document.knowledgeDocumentId(),
                        document.extractionVersion(), candidate.locator()),
                        boundedText(candidate.excerpt(), MAX_RAG_EXCERPT_CODE_POINTS)))
                .toList();
    }

    @Override public List<List<String>> findApplicableRules(List<ScenarioBundleDocumentSelection> rulebooks,
            UUID ownerId, UUID bundleId, List<RequiredSituation> situations) {
        if (rulebooks.isEmpty()) return situations.stream().map(ignored -> List.<String>of()).toList();
        List<String> queries = situations.stream().map(situation -> "For this story situation, find the applicable D&D rules for checks, saves, hazards, creatures, combat, and resolution. Situation: "
                + situation.summary()).toList();
        return searchBatch(ownerId, bundleId, rulebooks, queries, RULE_SEARCH_CANDIDATE_LIMIT).stream()
                .map(candidates -> candidates.stream().limit(RULE_EXCERPTS_PER_SITUATION)
                        .map(candidate -> boundedText(candidate.excerpt(), MAX_RAG_EXCERPT_CODE_POINTS)).toList()).toList();
    }

    private List<List<Candidate>> searchBatch(UUID ownerId, UUID bundleId,
            List<ScenarioBundleDocumentSelection> documents, List<String> queries, int candidateLimit) {
        if (queries.isEmpty()) return List.of();
        try {
            List<Map<String, Object>> scopes = documents.stream().map(document -> Map.<String, Object>of(
                    "documentId", document.knowledgeDocumentId().value(), "extractionVersion", document.extractionVersion(),
                    "documentType", document.documentType())).toList();
            String body = mapper.writeValueAsString(Map.of("ownerId", ownerId, "scenarioSourceBundleId", bundleId,
                    "scope", scopes, "activeLocators", List.of(), "queries", queries,
                    "denseLimit", candidateLimit));
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("internal/v1/evidence-candidates/preparation-search-batch"))
                    .timeout(timeout).header("Content-Type", "application/json").header("X-Internal-Token", token)
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) throw new IllegalStateException("preparation RAG lookup failed with status " + response.statusCode());
            BatchResponse result = mapper.readValue(response.body(), BatchResponse.class);
            if (!ownerId.equals(result.ownerId()) || !bundleId.equals(result.scenarioSourceBundleId()) || result.results() == null
                    || result.results().size() != queries.size()) throw new IllegalStateException("preparation RAG response scope does not match request");
            List<List<Candidate>> candidates = new java.util.ArrayList<>();
            for (int index = 0; index < queries.size(); index++) {
                BatchItem item = result.results().get(index);
                if (!queries.get(index).equals(item.query()) || item.candidates() == null) throw new IllegalStateException("preparation RAG query result is incomplete");
                for (Candidate candidate : item.candidates()) {
                    if (documents.stream().noneMatch(document -> document.knowledgeDocumentId().value().equals(candidate.documentId())
                            && document.extractionVersion() == candidate.extractionVersion()
                            && document.documentType().equals(candidate.documentType()))
                            || candidate.locator() == null || candidate.locator().isBlank()
                            || candidate.excerpt() == null || candidate.excerpt().isBlank()) {
                        throw new IllegalStateException("preparation RAG candidate is outside the selected document scope");
                    }
                }
                candidates.add(item.candidates().stream().limit(candidateLimit).toList());
            }
            return List.copyOf(candidates);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("preparation RAG lookup interrupted", failure);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("preparation RAG lookup failed", failure);
        }
    }

    private static String boundedText(String value, int maxCodePoints) {
        int codePoints = value.codePointCount(0, value.length());
        return codePoints <= maxCodePoints ? value : value.substring(0, value.offsetByCodePoints(0, maxCodePoints));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BatchResponse(UUID ownerId, UUID scenarioSourceBundleId, List<BatchItem> results) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BatchItem(String query, List<Candidate> candidates) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Candidate(UUID documentId, long extractionVersion, String documentType, String locator, String excerpt) {}
}
