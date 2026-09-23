package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.evidence.EvidenceAcquisitionContractException;
import com.dndmaster.adventure.evidence.EvidenceAcquisitionTransientException;
import com.dndmaster.adventure.evidence.EvidenceCandidate;
import com.dndmaster.adventure.evidence.EvidenceRerankRequest;
import com.dndmaster.adventure.evidence.EvidenceRerankerPort;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Internal adapter for the AI Game Master's fixed-policy relatedness ordering endpoint. */
public final class HttpEvidenceRerankerPort implements EvidenceRerankerPort {
    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final String internalToken;

    public HttpEvidenceRerankerPort(HttpClient client, URI baseUri, Duration timeout, ObjectMapper mapper, String internalToken) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.baseUri = Objects.requireNonNull(baseUri, "base uri must not be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.internalToken = required(internalToken, "internal token");
    }

    @Override
    public List<UUID> rerank(EvidenceRerankRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        try {
            String body = mapper.writeValueAsString(new Request(request.query(), request.policyId(), candidates(request.candidates())));
            var builder = HttpRequest.newBuilder(baseUri.resolve("internal/v1/gm/evidence-rerank"))
                    .timeout(timeout).header("Content-Type", "application/json").header("X-Internal-Token", internalToken);
            if (request.soloPlayerId() != null) builder.header("X-Solo-Player-Id", request.soloPlayerId().toString());
            HttpResponse<String> response = client.send(builder
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            if (isTransient(response.statusCode())) throw new EvidenceAcquisitionTransientException("evidence reranking is unavailable");
            if (response.statusCode() / 100 != 2) throw new EvidenceAcquisitionContractException("evidence reranking failed with status " + response.statusCode());
            List<UUID> ids = List.copyOf(mapper.readValue(response.body(), Response.class).orderedCandidateIds());
            validateIds(ids, request.candidates());
            return ids;
        } catch (IOException exception) {
            throw new EvidenceAcquisitionTransientException("evidence reranking transport failed");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EvidenceAcquisitionTransientException("evidence reranking interrupted");
        }
    }

    private static List<Candidate> candidates(List<EvidenceCandidate> candidates) {
        return candidates.stream().map(candidate -> new Candidate(candidate.id().toString(), candidate.documentType(), candidate.locator(), candidate.excerpt())).toList();
    }
    private static void validateIds(List<UUID> ids, List<EvidenceCandidate> candidates) {
        var available = candidates.stream().map(EvidenceCandidate::id).collect(java.util.stream.Collectors.toSet());
        if (ids.size() > 30 || ids.size() != new LinkedHashSet<>(ids).size() || !available.containsAll(ids)) {
            throw new EvidenceAcquisitionContractException("reranker returned invalid candidate identifiers");
        }
    }
    private static boolean isTransient(int status) { return status == 422 || status == 429 || status >= 500; }
    private static String required(String value, String name) { if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank"); return value; }
    record Request(String query, String taskContext, List<Candidate> candidates) {}
    record Candidate(String evidenceId, String documentType, String locator, String excerpt) {}
    @JsonIgnoreProperties(ignoreUnknown = true) record Response(List<UUID> orderedCandidateIds) {}
}
