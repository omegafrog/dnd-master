package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.runtime.InitialSourceContextProposalPort;
import com.dndmaster.adventure.domain.adventure.InitialSourceContextCandidate;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
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

public final class CrossContextHttpInitialSourceContextProposalGateway implements InitialSourceContextProposalPort {
    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper objectMapper;

    public CrossContextHttpInitialSourceContextProposalGateway(
            HttpClient client, URI baseUri, Duration timeout, ObjectMapper objectMapper) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.baseUri = Objects.requireNonNull(baseUri, "base uri must not be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "object mapper must not be null");
    }

    @Override
    public InitialSourceContextProposalResult propose(ScenarioPackage scenarioPackage, List<InitialSourceContextCandidate> candidates) {
        try {
            String body = objectMapper.writeValueAsString(new ProposalRequest(
                    scenarioPackage.packageId().toString(),
                    candidates.stream().map(CandidateRequest::from).toList()));
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("internal/v1/gm/initial-source-contexts"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("initial source context proposal failed with status " + response.statusCode());
            }
            ProposalResponse payload = objectMapper.readValue(response.body(), ProposalResponse.class);
            List<InitialSourceContextCandidate> proposed = payload.candidates() == null ? List.of() : payload.candidates().stream()
                    .filter(Objects::nonNull)
                    .map(CandidateResponse::toCandidate)
                    .toList();
            return new InitialSourceContextProposalResult(payload.status(), proposed);
        } catch (IOException exception) {
            throw new IllegalStateException("initial source context proposal failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("initial source context proposal interrupted", exception);
        }
    }

    record ProposalRequest(String packageId, List<CandidateRequest> candidates) {}

    record CandidateRequest(String knowledgeDocumentId, long extractionVersion, String locator, String excerpt, double score, String reason) {
        static CandidateRequest from(InitialSourceContextCandidate candidate) {
            return new CandidateRequest(candidate.knowledgeDocumentId().value().toString(), candidate.extractionVersion(),
                    candidate.locator(), candidate.excerpt(), candidate.score(), candidate.reason());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProposalResponse(String status, List<CandidateResponse> candidates) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CandidateResponse(String knowledgeDocumentId, long extractionVersion, String locator, String excerpt, double score, String reason) {
        InitialSourceContextCandidate toCandidate() {
            return new InitialSourceContextCandidate(
                    new com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId(java.util.UUID.fromString(knowledgeDocumentId)),
                    extractionVersion, locator, excerpt, score, reason);
        }
    }
}
