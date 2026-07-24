package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.scenario.LegacyScenarioIngestionPort;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.OwnerPlayerId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class CrossContextHttpLegacyScenarioIngestionGateway implements LegacyScenarioIngestionPort {
    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper objectMapper;

    public CrossContextHttpLegacyScenarioIngestionGateway(
            HttpClient client, URI baseUri, Duration timeout, ObjectMapper objectMapper) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.baseUri = Objects.requireNonNull(baseUri, "baseUri must not be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public ImportedKnowledgeDocument ingest(OwnerPlayerId ownerPlayerId, String originalFilename, byte[] content) {
        try {
            String boundary = "----dndmaster-legacy-" + UUID.randomUUID();
            byte[] body = multipartBody(boundary, ownerPlayerId, originalFilename, content);
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/api/v1/rulebooks?ownerPlayerId=" + ownerPlayerId.value()))
                    .timeout(timeout)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("legacy source ingestion failed");
            }
            JsonNode document = objectMapper.readTree(response.body()).path("documents").path(0);
            JsonNode knowledgeDocumentId = document.path("knowledgeDocumentId");
            if (knowledgeDocumentId.isMissingNode() || knowledgeDocumentId.isNull()) {
                throw new IllegalStateException("legacy source ingestion did not return a knowledge document id");
            }
            return new ImportedKnowledgeDocument(
                    new KnowledgeDocumentId(UUID.fromString(knowledgeDocumentId.asText())),
                    1L,
                    document.path("status").asText("ACCEPTED"));
        } catch (IOException exception) {
            throw new IllegalStateException("legacy source ingestion failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("legacy source ingestion interrupted", exception);
        }
    }

    private byte[] multipartBody(String boundary, OwnerPlayerId ownerPlayerId, String originalFilename, byte[] content)
            throws IOException {
        String contentDisposition = "Content-Disposition: form-data; name=\"documents\"\r\n";
        String documentsJson = objectMapper.writeValueAsString(List.of(new LegacyDocumentRequest(
                "legacy-" + ownerPlayerId.value() + "-" + originalFilename,
                "STORYBOOK",
                originalFilename)));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writePart(out, boundary, contentDisposition, "application/json", documentsJson.getBytes(StandardCharsets.UTF_8));
        writeFilePart(out, boundary, "files", originalFilename, content);
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static void writePart(
            ByteArrayOutputStream out, String boundary, String contentDisposition, String contentType, byte[] content)
            throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(contentDisposition.getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(content);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static void writeFilePart(
            ByteArrayOutputStream out, String boundary, String name, String filename, byte[] content) throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write("Content-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        out.write(content);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private record LegacyDocumentRequest(String idempotencyKey, String documentType, String originalFilename) {}
}
