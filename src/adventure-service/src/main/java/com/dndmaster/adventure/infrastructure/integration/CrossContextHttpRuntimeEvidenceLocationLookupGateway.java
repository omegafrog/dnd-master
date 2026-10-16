package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.runtime.RuntimeEvidence;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceLocationLookupPort;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceType;
import com.dndmaster.adventure.evidence.EvidenceAcquisitionContractException;
import com.dndmaster.adventure.evidence.EvidenceSearchScope;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Reads bounded published chunks from exact pages in the Adventure-confirmed scope. */
public final class CrossContextHttpRuntimeEvidenceLocationLookupGateway implements RuntimeEvidenceLocationLookupPort {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrossContextHttpRuntimeEvidenceLocationLookupGateway.class);
    private static final String PATH = "internal/v1/evidence-locations/lookup";
    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final String internalToken;

    public CrossContextHttpRuntimeEvidenceLocationLookupGateway(HttpClient client, URI baseUri, Duration timeout,
            ObjectMapper mapper, String internalToken) {
        this.client = Objects.requireNonNull(client);
        this.baseUri = Objects.requireNonNull(baseUri);
        this.timeout = Objects.requireNonNull(timeout);
        this.mapper = Objects.requireNonNull(mapper);
        if (internalToken == null || internalToken.isBlank()) throw new IllegalArgumentException("internal token must not be blank");
        this.internalToken = internalToken;
    }

    @Override
    public List<RuntimeEvidence> findOnSamePages(EvidenceSearchScope scope, List<RuntimeEvidence> anchors, int limit) {
        Objects.requireNonNull(scope);
        anchors = List.copyOf(Objects.requireNonNull(anchors));
        if (anchors.isEmpty() || anchors.size() > 10 || limit < 1 || limit > 4) {
            throw new IllegalArgumentException("same-page lookup request is outside its bound");
        }
        if (anchors.stream().anyMatch(anchor -> scope.documents().stream()
                .noneMatch(document -> document.id().equals(anchor.knowledgeDocumentId().value())
                        && document.extractionVersion() == anchor.extractionVersion()
                        && document.type().equals(anchor.evidenceType().name())))) {
            throw new EvidenceAcquisitionContractException("same-page lookup anchor is outside the confirmed scope");
        }
        String context = "sessionId=" + scope.sessionId() + " scenarioPackageId=" + scope.scenarioPackageId()
                + " anchorCount=" + anchors.size() + " requestedLimit=" + limit;
        long startedAt = com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics
                .begin(LOGGER, "rule_knowledge_same_page_lookup", context);
        try {
            List<RuntimeEvidence> result = new ArrayList<>();
            Map<String, List<RuntimeEvidence>> anchorsByDocument = new LinkedHashMap<>();
            for (RuntimeEvidence anchor : anchors) {
                String key = anchor.knowledgeDocumentId().value() + ":" + anchor.extractionVersion() + ":"
                        + anchor.evidenceType().name();
                anchorsByDocument.computeIfAbsent(key, ignored -> new ArrayList<>()).add(anchor);
            }
            for (List<RuntimeEvidence> documentAnchors : anchorsByDocument.values()) {
                RuntimeEvidence anchor = documentAnchors.getFirst();
                EvidenceSearchScope.Document selected = scope.documents().stream()
                        .filter(document -> document.id().equals(anchor.knowledgeDocumentId().value())
                                && document.extractionVersion() == anchor.extractionVersion()
                                && document.type().equals(anchor.evidenceType().name()))
                        .findFirst().orElseThrow(() -> new EvidenceAcquisitionContractException(
                                "same-page lookup anchor is outside the confirmed scope"));
                Request body = new Request(scope.ownerId(), scope.sessionId(), scope.scenarioPackageId(),
                        List.of(new Scope(selected.id(), selected.extractionVersion(), selected.type())),
                        documentAnchors.stream().map(RuntimeEvidence::locator).distinct().toList(), limit);
                String payload = mapper.writeValueAsString(body);
                HttpResponse<String> response = client.send(HttpRequest.newBuilder(baseUri.resolve(PATH)).timeout(timeout)
                        .header("X-Internal-Token", internalToken).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload)).build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 != 2) {
                    if (response.statusCode() >= 500 || response.statusCode() == 429) {
                        throw new IllegalStateException("same-page lookup returned " + response.statusCode(),
                                new IOException("retryable internal lookup response"));
                    }
                    throw new EvidenceAcquisitionContractException("same-page lookup returned " + response.statusCode());
                }
                Response parsed = mapper.readValue(response.body(), Response.class);
                if (parsed.ownerId() == null || !parsed.ownerId().equals(scope.ownerId())
                        || !scope.sessionId().equals(parsed.sessionId()) || !scope.scenarioPackageId().equals(parsed.scenarioPackageId())
                        || parsed.candidates() == null || parsed.candidates().size() > limit * body.locators().size()) {
                    throw new EvidenceAcquisitionContractException("same-page lookup response scope or count is invalid");
                }
                for (Candidate candidate : parsed.candidates()) {
                    if (candidate.chunkId() == null || !selected.id().equals(candidate.documentId())
                            || candidate.extractionVersion() != selected.extractionVersion()
                            || !selected.type().equals(candidate.documentType())
                            || candidate.locator() == null || candidate.locator().isBlank()
                            || candidate.excerpt() == null || candidate.excerpt().isBlank()) {
                        throw new EvidenceAcquisitionContractException("same-page lookup candidate scope mismatch expectedDocumentId="
                                + selected.id() + " actualDocumentId=" + candidate.documentId() + " expectedVersion="
                                + selected.extractionVersion() + " actualVersion=" + candidate.extractionVersion()
                                + " expectedType=" + selected.type() + " actualType=" + candidate.documentType()
                                + " chunkIdPresent=" + (candidate.chunkId() != null) + " locatorPresent="
                                + (candidate.locator() != null && !candidate.locator().isBlank()) + " excerptPresent="
                                + (candidate.excerpt() != null && !candidate.excerpt().isBlank()));
                    }
                    result.add(new RuntimeEvidence(RuntimeEvidenceType.valueOf(candidate.documentType()),
                            new com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId(candidate.documentId()),
                            candidate.extractionVersion(), candidate.locator(), candidate.excerpt()));
                }
            }
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.complete(LOGGER,
                    "rule_knowledge_same_page_lookup", context + " requestCount=" + anchorsByDocument.size() + " candidateCount="
                            + result.size() + " result=completed", startedAt);
            return List.copyOf(result);
        } catch (IOException exception) {
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fail(LOGGER,
                    "rule_knowledge_same_page_lookup", context, startedAt, exception);
            throw new IllegalStateException("same-page lookup transport failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fail(LOGGER,
                    "rule_knowledge_same_page_lookup", context, startedAt, exception);
            throw new IllegalStateException("same-page lookup was interrupted", exception);
        } catch (RuntimeException | Error failure) {
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fail(LOGGER,
                    "rule_knowledge_same_page_lookup", context, startedAt, failure);
            throw failure;
        }
    }

    private record Request(UUID ownerId, UUID sessionId, UUID scenarioPackageId, List<Scope> scope,
            List<String> locators, int limit) {}
    private record Scope(UUID documentId, long extractionVersion, String documentType) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Response(UUID ownerId, UUID sessionId, UUID scenarioPackageId, List<Candidate> candidates) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Candidate(UUID chunkId, UUID documentId, long extractionVersion,
            String documentType,
            String locator, String excerpt) {}
}
