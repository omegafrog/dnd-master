package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.scenario.compilation.ResolutionCandidate;
import com.dndmaster.adventure.application.scenario.compilation.ResolutionExtractionPort;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.ResolutionKind;
import com.dndmaster.adventure.domain.scenario.ScenarioResolutionDetail;
import com.dndmaster.adventure.domain.scenario.ResolutionVisibility;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import com.dndmaster.adventure.domain.scenario.SaveDc;
import com.dndmaster.adventure.domain.scenario.CasterSpellSaveDc;
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

public final class CrossContextHttpResolutionExtractionGateway implements ResolutionExtractionPort {
    private static final String SCENARIO_COMPILATION_OPERATION_PREFIX = "scenario-compilation:";
    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper objectMapper;

    public CrossContextHttpResolutionExtractionGateway(
            HttpClient client, URI baseUri, Duration timeout, ObjectMapper objectMapper) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.baseUri = Objects.requireNonNull(baseUri, "base uri must not be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "object mapper must not be null");
    }

    @Override
    public List<ResolutionCandidate> extract(ResolutionExtractionRequest request) {
        try {
            String operationId = operationId(request.operationId(), request.attempt() > 0);
            String body = objectMapper.writeValueAsString(new ResolutionExtractionWireRequest(
                    operationId,
                    request.excerpts().stream()
                            .map(excerpt -> new ResolutionExcerpt(
                                    excerpt.documentId().value(), excerpt.extractionVersion(), excerpt.locator(), excerpt.text()))
                            .toList(),
                    request.schemaVersion(),
                    request.promptVersion(), request.failedCandidate(), request.attempt(), request.diagnostics()));
            HttpRequest httpRequest = HttpRequest.newBuilder(baseUri.resolve("internal/v1/gm/resolution-candidates"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String detail = response.body() == null ? "" : response.body().trim();
                if (detail.length() > 500) detail = detail.substring(0, 500);
                throw new ResolutionExtractionException(
                        "resolution extraction failed with status " + response.statusCode()
                                + (detail.isEmpty() ? "" : ": " + detail));
            }
            ExtractionResponse extracted = objectMapper.readValue(response.body(), ExtractionResponse.class);
            return extracted.candidates() == null ? List.of() : extracted.candidates().stream()
                    .filter(Objects::nonNull)
                    .map(CrossContextHttpResolutionExtractionGateway::toCandidate).toList();
        } catch (IOException exception) {
            throw new ResolutionExtractionException("resolution extraction failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResolutionExtractionException("resolution extraction interrupted", exception);
        }
    }

    /** Makes the non-Story-Plan authoring responsibility explicit in provider diagnostics. */
    static String operationId(String operationId, boolean repair) {
        String base = Objects.requireNonNull(operationId, "operation id must not be null");
        String prefixed = base.startsWith(SCENARIO_COMPILATION_OPERATION_PREFIX)
                ? base : SCENARIO_COMPILATION_OPERATION_PREFIX + base;
        return repair ? prefixed + ":resolution-candidate-repair" : prefixed + ":resolution-candidates";
    }

    private static ResolutionCandidate toCandidate(CandidateResponse candidate) {
        SaveDc dc = candidate.dc();
        if (dc == null && candidate.kind() == ResolutionKind.SAVING_THROW
                && candidate.sourceQuote() != null
                && candidate.sourceQuote().matches("(?is).*\\bspell\\s+save\\s+DC\\b.*")) {
            dc = new CasterSpellSaveDc();
        }
        return new ResolutionCandidate(
                candidate.kind(), candidate.abilityOrSkill(), dc, candidate.diceExpression(),
                candidate.visibility(), candidate.sourceQuote(),
                (candidate.sourceRefs() == null ? List.<SourceReferenceResponse>of() : candidate.sourceRefs()).stream()
                        .map(ref -> new ScenarioSourceReference(
                                new KnowledgeDocumentId(ref.documentId()), ref.extractionVersion(), ref.locator()))
                        .toList(),
                candidate.provenance(),
                candidate.detail());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ExtractionResponse(List<CandidateResponse> candidates) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CandidateResponse(
            ResolutionKind kind,
            String abilityOrSkill,
            SaveDc dc,
            String diceExpression,
            ResolutionVisibility visibility,
            String sourceQuote,
            List<SourceReferenceResponse> sourceRefs,
            String provenance,
            ScenarioResolutionDetail detail) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SourceReferenceResponse(java.util.UUID documentId, long extractionVersion, String locator) {}

    record ResolutionExtractionWireRequest(
            String operationId, List<ResolutionExcerpt> excerpts, String schemaVersion, String promptVersion,
            ResolutionCandidate failedCandidate, int attempt, List<String> diagnostics) {}

    record ResolutionExcerpt(java.util.UUID documentId, long extractionVersion, String locator, String text) {}
}
