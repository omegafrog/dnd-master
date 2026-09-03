package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.runtime.EvidencePack;
import com.dndmaster.adventure.application.storyplan.SemanticJudgeProvider;
import com.dndmaster.adventure.domain.adventure.SemanticVerdict;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Logical AI Adventure boundary for semantic Story Plan judging. */
public final class CrossContextHttpStoryPlanSemanticJudgeGateway implements SemanticJudgeProvider {
    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final String internalToken;

    public CrossContextHttpStoryPlanSemanticJudgeGateway(HttpClient client, URI baseUri, Duration timeout,
            ObjectMapper mapper, String internalToken) {
        this.client = Objects.requireNonNull(client);
        this.baseUri = Objects.requireNonNull(baseUri);
        this.timeout = Objects.requireNonNull(timeout);
        this.mapper = Objects.requireNonNull(mapper);
        if (internalToken == null || internalToken.isBlank()) throw new IllegalArgumentException("semantic judge internal token must not be blank");
        this.internalToken = internalToken;
    }

    @Override
    public Response judge(Request request) {
        try {
            EvidencePack pack = request.evidencePack();
            List<Map<String, Object>> citations = pack.all().stream().map(evidence -> Map.<String, Object>of(
                    "documentType", evidence.evidenceType().name(),
                    "documentId", evidence.knowledgeDocumentId().value().toString(),
                    "extractionVersion", evidence.extractionVersion(), "locator", evidence.locator(),
                    "confidence", 1.0,
                    "citationKey", evidence.citationKey() == null ? "" : evidence.citationKey())).toList();
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("operationId", "semantic-" + System.nanoTime());
            body.put("configuration", Map.of("endingCount", 2, "adventureLength", "STANDARD"));
            body.put("sourceDocuments", List.of());
            body.put("resolutionEvidence", List.of());
            body.put("maps", List.of());
            body.put("citations", citations);
            body.put("generatedMarkdown", request.candidate());
            if (request.ownerId() != null) {
                List<Map<String, Object>> documents = pack.all().stream()
                        .map(evidence -> Map.<String, Object>of(
                                "documentType", evidence.evidenceType().name(),
                                "documentId", evidence.knowledgeDocumentId().value(),
                                "extractionVersion", evidence.extractionVersion()))
                        .distinct().toList();
                body.put("retrievalContext", Map.of("ownerId", request.ownerId(), "documents", documents));
            }
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(
                    baseUri.resolve("internal/v1/gm/adventure-story-plan/verify"))
                    .timeout(timeout).header("Content-Type", "application/json")
                    .header("X-Internal-Token", internalToken)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new Response(SemanticVerdict.judgeUnavailable("semantic judge failed with status " + response.statusCode()));
            }
            JsonNode root = mapper.readTree(response.body());
            String status = root.path("status").asText("").toUpperCase(java.util.Locale.ROOT);
            List<String> violations = new ArrayList<>();
            root.path("violations").forEach(node -> violations.add(node.asText()));
            if ("PASS".equals(status)) {
                return new Response(SemanticVerdict.compatible(1.0, "storyPlan", "AI Adventure verifier accepted the plan", java.util.Set.of(), java.util.Set.of()));
            }
            if ("FAIL".equals(status)) {
                String summary = violations.stream().filter(value -> value != null && !value.isBlank()).limit(3).collect(Collectors.joining("; "));
                return new Response(SemanticVerdict.contradictory(.9, "storyPlan", summary.isBlank() ? "AI Adventure verifier rejected the plan" : summary, java.util.Set.of(), java.util.Set.of()));
            }
            return new Response(SemanticVerdict.judgeUnavailable("semantic judge returned an unknown status"));
        } catch (Exception failure) {
            return new Response(SemanticVerdict.judgeUnavailable("semantic judge unavailable: " + failure.getClass().getSimpleName()));
        }
    }
}
