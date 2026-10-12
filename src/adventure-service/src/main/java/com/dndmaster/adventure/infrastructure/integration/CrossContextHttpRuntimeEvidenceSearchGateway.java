package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.runtime.RuntimeEvidence;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceSearchPort;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceSearchRequest;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceType;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CrossContextHttpRuntimeEvidenceSearchGateway implements RuntimeEvidenceSearchPort {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrossContextHttpRuntimeEvidenceSearchGateway.class);
    private static final String REQUEST_FIELDS = "ownerId,sessionId,scenarioPackageId,stageKey,actionIntent,scope,activeLocators,excludedEvidenceIds,query,denseLimit";
    private final HttpClient httpClient;
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper objectMapper;
    private final String internalToken;

    public CrossContextHttpRuntimeEvidenceSearchGateway(
            HttpClient httpClient, URI baseUri, Duration timeout, ObjectMapper objectMapper, String internalToken) {
        this.httpClient = Objects.requireNonNull(httpClient, "http client must not be null");
        this.baseUri = Objects.requireNonNull(baseUri, "base uri must not be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "object mapper must not be null");
        this.internalToken = requireInternalToken(internalToken);
    }

    @Override
    public List<RuntimeEvidence> search(RuntimeEvidenceSearchRequest request) {
        if (request.knowledgeDocumentIds().isEmpty()) return List.of();
        long startedAt = com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.begin(
                LOGGER, "runtime_evidence_search", "sessionId=" + request.sessionId() + " scenarioPackageId="
                        + request.scenarioPackageId() + " documentScopeCount=" + request.knowledgeDocumentIds().size());
        try {
            CandidateSearchResponse response = post("internal/v1/evidence-candidates/search",
                    new CandidateSearchRequest(request.ownerPlayerId().value(), request.sessionId().value(),
                            request.scenarioPackageId(), request.contextKey(), request.actionIntent(),
                            request.knowledgeDocumentIds().stream().map(id -> new CandidateScope(
                                    id, extractionVersion(request, id), documentType(request.evidenceType()))).toList(),
                            activeLocators(request), List.of(), request.action(), 30), CandidateSearchResponse.class);
            if (!request.ownerPlayerId().value().equals(response.ownerId())
                    || !request.sessionId().value().equals(response.sessionId())
                    || !request.scenarioPackageId().equals(response.scenarioPackageId())
                    || response.candidates() == null) {
                throw new IllegalStateException("runtime evidence response scope does not match its request");
            }
            Map<UUID, Long> requestedVersions = request.knowledgeDocumentIds().stream()
                    .collect(java.util.stream.Collectors.toMap(id -> id, id -> extractionVersion(request, id), (left, right) -> left));
            List<RuntimeEvidence> evidence = response.candidates().stream()
                    .peek(candidate -> {
                        if (candidate == null || !documentType(request.evidenceType()).equals(candidate.documentType())
                                || !requestedVersions.containsKey(candidate.documentId())
                                || requestedVersions.get(candidate.documentId()) != candidate.extractionVersion()) {
                            throw new IllegalStateException("runtime evidence candidate is outside the requested scope");
                        }
                    })
                    .map(candidate -> new RuntimeEvidence(request.evidenceType(), new KnowledgeDocumentId(candidate.documentId()),
                            candidate.extractionVersion(), candidate.locator(), candidate.excerpt(),
                            request.evidenceType().name() + ":" + candidate.documentId() + ":"
                                    + candidate.extractionVersion() + ":" + candidate.locator()))
                    .toList();
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.complete(LOGGER,
                    "runtime_evidence_search", "candidateCount=" + response.candidates().size()
                            + " resultCount=" + evidence.size() + " requestFields=" + REQUEST_FIELDS, startedAt);
            return evidence;
        } catch (Exception exception) {
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fail(LOGGER,
                    "runtime_evidence_search", "documentScopeCount=" + request.knowledgeDocumentIds().size()
                            + " expectedRequestFields=" + REQUEST_FIELDS + " recovery=preserve_failure", startedAt, exception);
            throw new IllegalStateException("runtime evidence search failed", exception);
        }
    }

    private static long extractionVersion(RuntimeEvidenceSearchRequest request, UUID documentId) {
        Long packageVersion = request.extractionVersions().get(documentId);
        if (packageVersion != null && packageVersion > 0) return packageVersion;
        return request.activeSourceContext() != null
                && request.activeSourceContext().knowledgeDocumentId().value().equals(documentId)
                ? request.activeSourceContext().extractionVersion() : 1L;
    }

    private static List<String> activeLocators(RuntimeEvidenceSearchRequest request) {
        return request.activeSourceContext() == null ? List.of() : List.of(request.activeSourceContext().locator());
    }

    private static String documentType(RuntimeEvidenceType evidenceType) {
        return evidenceType == RuntimeEvidenceType.STORYBOOK ? "STORYBOOK" : "RULEBOOK";
    }

    private <T> T post(String path, Object payload, Class<T> responseType) throws Exception {
        String body = objectMapper.writeValueAsString(payload);
        HttpRequest httpRequest = HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(timeout)
                .header("X-Internal-Token", internalToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) {
            var requestJson = objectMapper.readTree(body);
            LOGGER.info("dev_runtime_evidence_search stage=http status={} expectedRequestFields={} actualRequestFields={} scopeCount={} activeLocatorCount={} excludedEvidenceCount={} queryChars={} responseChars={}",
                    response.statusCode(), REQUEST_FIELDS, fields(requestJson), requestJson.path("scope").size(),
                    requestJson.path("activeLocators").size(), requestJson.path("excludedEvidenceIds").size(),
                    requestJson.path("query").asText().length(), response.body().length());
        }
        if (response.statusCode() / 100 != 2) throw new IllegalStateException("runtime evidence search returned " + response.statusCode());
        return objectMapper.readValue(response.body(), responseType);
    }

    private static String fields(com.fasterxml.jackson.databind.JsonNode node) {
        java.util.List<String> names = new java.util.ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return String.join(",", names);
    }

    private static String requireInternalToken(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("internal token must not be blank");
        return value;
    }

    record CandidateSearchRequest(UUID ownerId, UUID sessionId, UUID scenarioPackageId, String stageKey, String actionIntent,
                                  List<CandidateScope> scope, List<String> activeLocators,
                                  List<String> excludedEvidenceIds, String query, int denseLimit) {}
    record CandidateScope(UUID documentId, long extractionVersion, String documentType) {}
    record CandidateSearchResponse(UUID ownerId, UUID sessionId, UUID scenarioPackageId, List<Candidate> candidates) {}
    record Candidate(UUID chunkId, UUID documentId, long extractionVersion, String documentType,
                     String locator, String excerpt) {}
}
