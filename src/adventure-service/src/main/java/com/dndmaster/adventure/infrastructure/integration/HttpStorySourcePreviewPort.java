package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.scenario.preparation.StorySourcePreviewPort;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Reads the published, located original story text from the selected document. */
public final class HttpStorySourcePreviewPort implements StorySourcePreviewPort {
    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final String token;

    public HttpStorySourcePreviewPort(HttpClient client, URI baseUri, Duration timeout, ObjectMapper mapper, String token) {
        this.client = Objects.requireNonNull(client);
        this.baseUri = Objects.requireNonNull(baseUri);
        this.timeout = Objects.requireNonNull(timeout);
        this.mapper = Objects.requireNonNull(mapper);
        if (token == null || token.isBlank()) throw new IllegalArgumentException("internal token is required");
        this.token = token;
    }

    @Override public List<Passage> load(ScenarioBundleDocumentSelection document) {
        if (!"STORYBOOK".equalsIgnoreCase(document.documentType())) throw new IllegalArgumentException("story document is required");
        try {
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("api/v1/rulebooks/"
                            + document.knowledgeDocumentId().value() + "/source-preview"))
                    .timeout(timeout).header("X-Internal-Token", token).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) throw new IllegalStateException("original story lookup failed with status " + response.statusCode());
            Preview preview = mapper.readValue(response.body(), Preview.class);
            if (!document.knowledgeDocumentId().value().equals(preview.knowledgeDocumentId())
                    || !"STORYBOOK".equalsIgnoreCase(preview.documentType())
                    || document.extractionVersion() != preview.extractionVersion()
                    || !"INDEXED".equals(preview.status())
                    || preview.warnings() == null || !preview.warnings().isEmpty()
                    || preview.spans() == null || preview.spans().isEmpty()
                    || preview.content() == null || preview.content().isBlank()) {
                throw new IllegalStateException("original story is incomplete or outside the selected published document");
            }
            List<Passage> passages = preview.spans().stream().map(span -> {
                if (span.locator() == null || span.locator().isBlank() || span.text() == null || span.text().isBlank()) {
                    throw new IllegalStateException("original story has an unlocated or empty passage");
                }
                return new Passage(new ScenarioSourceReference(document.knowledgeDocumentId(),
                        document.extractionVersion(), span.locator()), span.text());
            }).toList();
            if (passages.stream().map(Passage::sourceRef).distinct().count() != passages.size()) {
                throw new IllegalStateException("original story passage locators must be unique");
            }
            String reconstructed = String.join("\n\n", passages.stream().map(Passage::text).toList()).trim();
            if (!preview.content().replaceAll("\\s+", "").equals(reconstructed.replaceAll("\\s+", ""))) {
                throw new IllegalStateException("original story passages do not cover the source preview content");
            }
            return passages;
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("original story lookup interrupted", failure);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("original story lookup failed", failure);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Preview(UUID knowledgeDocumentId, String documentType, String status, String content,
            long extractionVersion, List<String> warnings, List<Span> spans) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Span(String locator, String text) {}
}
