package com.dndmaster.aigamemaster.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;
import java.time.Duration;

public final class HttpStoryRetrievalAdapter implements RetrievalEvaluationPort {
    private final RestClient client;
    public HttpStoryRetrievalAdapter(String baseUrl, ObjectMapper mapper) { this(baseUrl, mapper, Duration.ofSeconds(5)); }
    public HttpStoryRetrievalAdapter(String baseUrl, ObjectMapper mapper, Duration timeout) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
    @Override public RetrievalEvaluationResult retrieve(RetrievalEvaluationCase c, int limit) {
        long started = System.nanoTime();
        try {
            var documents = c.searchScope().stream().map(r -> new Scope(UUID.fromString(r.documentId()), version(r.version()))).distinct().toList();
            JsonNode body = client.post().uri("/internal/v1/story-sources/search").header("Authorization", "Bearer " + c.ownerId())
                    .body(new Request(c.ownerId(), documents, java.util.List.of(), c.query(), limit)).retrieve().body(JsonNode.class);
            var candidates = new ArrayList<RetrievalCandidate>();
            for (JsonNode item : body.path("evidence")) candidates.add(new RetrievalCandidate(
                    new RetrievalReference(item.path("knowledgeDocumentId").asText(), item.path("locator").asText(), "v" + item.path("extractionVersion").asLong()),
                    body.path("ownerId").asText(c.ownerId()), body.path("sessionId").asText(c.sessionId()),
                    body.path("packageId").asText(c.packageId()), item.path("score").asDouble()));
            return new RetrievalEvaluationResult(c.id(), candidates, (System.nanoTime() - started) / 1_000_000d);
        } catch (RuntimeException failure) { throw new IllegalStateException("story retrieval evaluation failed", failure); }
    }
    private static long version(String value) { return Long.parseLong(value.replace("v", "")); }
    private record Request(String ownerId, java.util.List<Scope> documents, java.util.List<String> activeLocators, String situation, int limit) {}
    private record Scope(UUID documentId, long extractionVersion) {}
}
