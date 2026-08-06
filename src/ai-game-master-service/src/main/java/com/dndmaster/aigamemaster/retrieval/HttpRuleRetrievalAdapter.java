package com.dndmaster.aigamemaster.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;

public final class HttpRuleRetrievalAdapter implements RetrievalEvaluationPort {
    private final RestClient client;
    private final ObjectMapper mapper;
    public HttpRuleRetrievalAdapter(String baseUrl, ObjectMapper mapper) { this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(new JdkClientHttpRequestFactory(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build())).build(); this.mapper = mapper; }
    @Override public RetrievalEvaluationResult retrieve(RetrievalEvaluationCase c, int limit) {
        long started = System.nanoTime();
        try {
            var ids = c.searchScope().stream().map(RetrievalReference::documentId).distinct().map(UUID::fromString).toList();
            JsonNode body = client.post().uri("/internal/v1/rule-evidence/search").header("Authorization", "Bearer " + c.ownerId())
                    .body(new Request(c.ownerId(), ids, c.query(), "RULE", limit)).retrieve().body(JsonNode.class);
            var candidates = new ArrayList<RetrievalCandidate>();
            for (JsonNode item : body.path("evidence")) candidates.add(new RetrievalCandidate(
                    new RetrievalReference(item.path("rulebookId").asText(), item.path("locator").asText(), "v1"),
                    body.path("ownerId").asText(c.ownerId()), body.path("sessionId").asText(c.sessionId()),
                    body.path("packageId").asText(c.packageId()), item.path("score").asDouble()));
            return new RetrievalEvaluationResult(c.id(), candidates, elapsedMs(started));
        } catch (RuntimeException failure) { throw new IllegalStateException("rule retrieval evaluation failed", failure); }
    }
    private static double elapsedMs(long started) { return (System.nanoTime() - started) / 1_000_000d; }
    private record Request(String ownerId, java.util.List<UUID> rulebookIds, String situation, String queryIntent, int limit) {}
}
