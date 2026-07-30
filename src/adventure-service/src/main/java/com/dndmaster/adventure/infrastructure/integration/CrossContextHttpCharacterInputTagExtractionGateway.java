package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.scenario.blueprint.CharacterInputTagExtractionPort;
import com.dndmaster.adventure.application.scenario.blueprint.CharacterInputTagExtractionPort.CharacterInputTagCandidate;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.InputMode;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
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

public final class CrossContextHttpCharacterInputTagExtractionGateway implements CharacterInputTagExtractionPort {
    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper objectMapper;

    public CrossContextHttpCharacterInputTagExtractionGateway(HttpClient client, URI baseUri, Duration timeout, ObjectMapper objectMapper) {
        this.client = Objects.requireNonNull(client);
        this.baseUri = Objects.requireNonNull(baseUri);
        this.timeout = Objects.requireNonNull(timeout);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override public List<CharacterInputTagCandidate> extract(Request request) {
        try {
            String body = objectMapper.writeValueAsString(new WireRequest(request.operationId(), request.excerpts().stream()
                    .map(e -> new Excerpt(e.documentId().value(), e.extractionVersion(), e.locator(), e.text())).toList(),
                    request.schemaVersion(), request.promptVersion(), request.instruction()));
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(baseUri.resolve("internal/v1/gm/character-input-tags"))
                    .timeout(timeout).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new CharacterInputTagExtractionException("character tag extraction failed with status " + response.statusCode());
            Response parsed = objectMapper.readValue(response.body(), Response.class);
            if (parsed.candidates() == null) return List.of();
            return parsed.candidates().stream().filter(Objects::nonNull)
                    .map(CrossContextHttpCharacterInputTagExtractionGateway::toCandidate)
                    .filter(candidate -> grounded(candidate, request.excerpts())).toList();
        } catch (IOException e) { throw new CharacterInputTagExtractionException("character tag extraction failed", e); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new CharacterInputTagExtractionException("character tag extraction interrupted", e); }
    }

    static boolean grounded(CharacterInputTagCandidate candidate, List<SourceExcerpt> excerpts) {
        return !candidate.evidence().isEmpty() && candidate.evidence().stream().allMatch(reference -> excerpts.stream().anyMatch(excerpt ->
                reference.knowledgeDocumentId().equals(excerpt.documentId())
                        && reference.extractionVersion() == excerpt.extractionVersion()
                        && reference.locator().equals(excerpt.locator())))
                && candidate.optionDetails().stream().allMatch(detail -> !detail.evidence().isEmpty()
                        && detail.evidence().stream().allMatch(reference -> excerpts.stream().anyMatch(excerpt ->
                        reference.knowledgeDocumentId().equals(excerpt.documentId())
                                && reference.extractionVersion() == excerpt.extractionVersion()
                                && reference.locator().equals(excerpt.locator()))));
    }

    private static CharacterInputTagCandidate toCandidate(Candidate c) {
        List<ScenarioSourceReference> evidence = c.evidence() == null ? List.of() : c.evidence().stream()
                .filter(Objects::nonNull).map(e -> new ScenarioSourceReference(new KnowledgeDocumentId(e.documentId()), e.extractionVersion(), e.locator())).toList();
        List<CharacterInputTagCandidate.OptionDetail> optionDetails = c.optionDetails() == null ? List.of() : c.optionDetails().stream()
                .filter(Objects::nonNull).map(detail -> new CharacterInputTagCandidate.OptionDetail(detail.value(), detail.label(),
                        detail.description(), detail.sourceQuote() == null || detail.sourceQuote().isBlank()
                                ? (c.sourceQuote() == null || c.sourceQuote().isBlank() ? detail.value() : c.sourceQuote())
                                : detail.sourceQuote(), detail.evidence() == null ? List.of() : detail.evidence().stream()
                        .filter(Objects::nonNull).map(e -> new ScenarioSourceReference(new KnowledgeDocumentId(e.documentId()),
                                e.extractionVersion(), e.locator())).toList())).toList();
        return new CharacterInputTagCandidate(c.key(), c.label(), c.parentKey(), c.required(), c.inputMode(), c.options(), c.suggestions(), c.confidence(), evidence, c.sourceQuote(), c.sourceType(), optionDetails);
    }

    public static final class CharacterInputTagExtractionException extends RuntimeException { public CharacterInputTagExtractionException(String message) { super(message); } public CharacterInputTagExtractionException(String message, Throwable cause) { super(message, cause); } }
    record WireRequest(String operationId, List<Excerpt> excerpts, String schemaVersion, String promptVersion,
                       String instruction) {}
    record Excerpt(java.util.UUID documentId, long extractionVersion, String locator, String text) {}
    @JsonIgnoreProperties(ignoreUnknown = true) record Response(List<Candidate> candidates) {}
    @JsonIgnoreProperties(ignoreUnknown = true) record Candidate(String key, String label, String parentKey, boolean required, InputMode inputMode, List<String> options, List<OptionDetail> optionDetails, List<String> suggestions, String confidence, List<Evidence> evidence, String sourceQuote, String sourceType) {}
    record OptionDetail(String value, String label, String description, String sourceQuote, List<Evidence> evidence) {}
    record Evidence(java.util.UUID documentId, long extractionVersion, String locator) {}
}
