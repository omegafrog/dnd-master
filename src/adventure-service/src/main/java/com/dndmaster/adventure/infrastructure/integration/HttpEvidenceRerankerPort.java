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
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(HttpEvidenceRerankerPort.class);
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
            String body = mapper.writeValueAsString(new Request(request.soloPlayerId(), request.query(), request.policyId(), candidates(request.candidates())));
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(baseUri.resolve("internal/v1/gm/evidence-rerank"))
                    .timeout(timeout).header("Content-Type", "application/json").header("X-Internal-Token", internalToken)
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            devLog("dev_agent_http operation=evidence_rerank status={} policy={} queryFingerprint={} candidateCount={} candidates={} response={}",
                    response.statusCode(), request.policyId(), com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fingerprint(request.query()),
                    request.candidates().size(), request.candidates().stream().map(candidate -> candidate.id() + ":"
                            + candidate.documentType() + ":" + candidate.locator()).toList(), response.statusCode() / 100 == 2 ? "" :
                            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.safeBody(response.body()));
            if (isTransient(response.statusCode())) throw new EvidenceAcquisitionTransientException(
                    "evidence reranking is unavailable (HTTP " + response.statusCode() + ")");
            if (response.statusCode() / 100 != 2) {
                if (response.statusCode() == 422) {
                    String responseMarker = response.body().contains("EVIDENCE_MODEL_OUTPUT_INVALID")
                            ? "EVIDENCE_MODEL_OUTPUT_INVALID" : "UNCLASSIFIED_422";
                    devLog("dev_evidence_rerank_contract status={} policy={} candidateCount={} responseChars={} responseMarker={} response={}",
                            response.statusCode(), request.policyId(), request.candidates().size(), response.body().length(), responseMarker,
                            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.safeBody(response.body()));
                }
                throw new EvidenceAcquisitionContractException("evidence reranking failed with status " + response.statusCode());
            }
            List<UUID> ids = List.copyOf(mapper.readValue(response.body(), Response.class).orderedCandidateIds());
            validateIds(ids, request.candidates());
            devLog("dev_agent_response operation=evidence_rerank policy={} queryFingerprint={} inputCount={} orderedIds={}",
                    request.policyId(), com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fingerprint(request.query()),
                    request.candidates().size(), ids);
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
    private static boolean isTransient(int status) { return status == 429 || status >= 500; }
    private static void devLog(String pattern, Object... args) {
        if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) LOGGER.info(pattern, args);
    }
    private static String required(String value, String name) { if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank"); return value; }
    record Request(UUID soloPlayerId, String query, String taskContext, List<Candidate> candidates) {}
    record Candidate(String evidenceId, String documentType, String locator, String excerpt) {}
    @JsonIgnoreProperties(ignoreUnknown = true) record Response(List<UUID> orderedCandidateIds) {}
}
