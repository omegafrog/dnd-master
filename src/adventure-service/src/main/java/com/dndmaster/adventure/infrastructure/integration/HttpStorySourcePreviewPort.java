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

/** Reads every published, located RAG chunk from the selected story document. */
public final class HttpStorySourcePreviewPort implements StorySourcePreviewPort {
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
        try {
            List<String> rankedLocators = searchBatch(ownerId, bundleId, List.of(document), List.of(
                    "Find story-essential situations, explicit prerequisite relationships, intermediate events, and the ending."))
                    .getFirst().stream().map(Candidate::locator).distinct().toList();
            List<Passage> passages = new java.util.ArrayList<>();
            java.util.Set<UUID> chunkIds = new java.util.HashSet<>();
            long expectedCount = -1;
            for (int offset = 0; expectedCount < 0 || offset < expectedCount; offset += 50) {
                URI uri = baseUri.resolve("internal/v1/rag-chunks/preparation?ownerId=" + ownerId + "&scenarioSourceBundleId=" + bundleId
                        + "&documentId=" + document.knowledgeDocumentId().value() + "&extractionVersion=" + document.extractionVersion()
                        + "&offset=" + offset + "&limit=50");
                HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout).header("X-Internal-Token", token).GET().build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 != 2) throw new IllegalStateException("indexed story lookup failed with status " + response.statusCode());
                ChunkPage page = mapper.readValue(response.body(), ChunkPage.class);
                if (!ownerId.equals(page.ownerId()) || !bundleId.equals(page.scenarioSourceBundleId())
                        || !document.knowledgeDocumentId().value().equals(page.documentId())
                        || page.extractionVersion() != document.extractionVersion() || page.offset() != offset
                        || page.totalCount() < 1 || page.chunks() == null || page.chunks().isEmpty()
                        || (expectedCount >= 0 && expectedCount != page.totalCount())) {
                    throw new IllegalStateException("indexed story page scope or coverage does not match the selected document");
                }
                expectedCount = page.totalCount();
                for (Chunk chunk : page.chunks()) {
                    if (chunk.chunkId() == null || !chunkIds.add(chunk.chunkId()) || chunk.originalLocator() == null
                            || chunk.originalLocator().isBlank() || chunk.content() == null || chunk.content().isBlank()) {
                        throw new IllegalStateException("indexed story contains an invalid or duplicate chunk");
                    }
                    passages.add(new Passage(new ScenarioSourceReference(document.knowledgeDocumentId(),
                            document.extractionVersion(), chunk.originalLocator()), chunk.content()));
                }
                if (page.chunks().size() > 50 || offset + page.chunks().size() > expectedCount) {
                    throw new IllegalStateException("indexed story page exceeds the declared coverage");
                }
            }
            if (passages.size() != expectedCount) {
                throw new IllegalStateException("indexed story chunks do not cover the declared chunk count");
            }
            Map<String, Integer> rank = new java.util.HashMap<>();
            for (int index = 0; index < rankedLocators.size(); index++) rank.putIfAbsent(rankedLocators.get(index), index);
            passages.sort(java.util.Comparator.comparingInt(item -> rank.getOrDefault(item.sourceRef().locator(), Integer.MAX_VALUE)));
            return List.copyOf(passages);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("original story lookup interrupted", failure);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("original story lookup failed", failure);
        }
    }

    @Override public List<List<String>> findApplicableRules(List<ScenarioBundleDocumentSelection> rulebooks,
            UUID ownerId, UUID bundleId, List<RequiredSituation> situations) {
        if (rulebooks.isEmpty()) return situations.stream().map(ignored -> List.<String>of()).toList();
        List<String> queries = situations.stream().map(situation -> "For this story situation, find the applicable D&D rules for checks, saves, hazards, creatures, combat, and resolution. Situation: "
                + situation.summary()).toList();
        return searchBatch(ownerId, bundleId, rulebooks, queries).stream()
                .map(candidates -> candidates.stream().map(Candidate::excerpt).toList()).toList();
    }

    private List<List<Candidate>> searchBatch(UUID ownerId, UUID bundleId,
            List<ScenarioBundleDocumentSelection> documents, List<String> queries) {
        if (queries.isEmpty()) return List.of();
        try {
            List<Map<String, Object>> scopes = documents.stream().map(document -> Map.<String, Object>of(
                    "documentId", document.knowledgeDocumentId().value(), "extractionVersion", document.extractionVersion(),
                    "documentType", document.documentType())).toList();
            String body = mapper.writeValueAsString(Map.of("ownerId", ownerId, "scenarioSourceBundleId", bundleId,
                    "scope", scopes, "activeLocators", List.of(), "queries", queries, "denseLimit", 30, "bm25Limit", 30));
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
                candidates.add(List.copyOf(item.candidates()));
            }
            return List.copyOf(candidates);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("preparation RAG lookup interrupted", failure);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("preparation RAG lookup failed", failure);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChunkPage(UUID ownerId, UUID scenarioSourceBundleId, UUID documentId, long extractionVersion,
            long totalCount, int offset, List<Chunk> chunks) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Chunk(UUID chunkId, int sequence, String content, String originalLocator, int pageNumber, List<String> sectionPath) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BatchResponse(UUID ownerId, UUID scenarioSourceBundleId, List<BatchItem> results) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BatchItem(String query, List<Candidate> candidates) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Candidate(UUID documentId, long extractionVersion, String documentType, String locator, String excerpt) {}
}
