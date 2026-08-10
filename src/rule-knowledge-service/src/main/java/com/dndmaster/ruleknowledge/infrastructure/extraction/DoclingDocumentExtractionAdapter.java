package com.dndmaster.ruleknowledge.infrastructure.extraction;

import com.dndmaster.ruleknowledge.application.extraction.DocumentExtractionPort;
import com.dndmaster.ruleknowledge.domain.extraction.DocumentExtractionResult;
import com.dndmaster.ruleknowledge.domain.extraction.DocumentImage;
import com.dndmaster.ruleknowledge.domain.extraction.DocumentNode;
import com.dndmaster.ruleknowledge.domain.extraction.DocumentNodeType;
import com.dndmaster.ruleknowledge.domain.extraction.DocumentTable;
import com.dndmaster.ruleknowledge.domain.extraction.ExtractionWarning;
import com.dndmaster.ruleknowledge.domain.rulebook.BoundingBox;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

public final class DoclingDocumentExtractionAdapter implements DocumentExtractionPort {
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final URI endpoint;
    private final Duration timeout;

    public DoclingDocumentExtractionAdapter(String baseUrl, Duration timeout) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), new ObjectMapper(), baseUrl, timeout);
    }

    public DoclingDocumentExtractionAdapter(ObjectMapper mapper, String baseUrl, Duration timeout) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), mapper, baseUrl, timeout);
    }

    DoclingDocumentExtractionAdapter(HttpClient client, ObjectMapper mapper, String baseUrl, Duration timeout) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.endpoint = URI.create(Objects.requireNonNull(baseUrl, "baseUrl must not be null").replaceAll("/$", "") + "/extract");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
    }

    @Override
    public DocumentExtractionResult extract(RulebookFormat format, byte[] content) {
        Objects.requireNonNull(format, "format must not be null");
        Objects.requireNonNull(content, "content must not be null");
        try {
            String body = mapper.writeValueAsString(new Request(format.name(), Base64.getEncoder().encodeToString(content)));
            HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 500 || response.statusCode() == 408 || response.statusCode() == 429) {
                throw new DoclingExtractionException("Docling unavailable: HTTP " + response.statusCode(), true);
            }
            if (response.statusCode() != 200) {
                throw new DoclingExtractionException("Docling rejected document: HTTP " + response.statusCode(), false);
            }
            return parse(response.body());
        } catch (DoclingExtractionException exception) {
            throw exception;
        } catch (java.io.IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new DoclingExtractionException("Docling request failed", exception, true);
        } catch (RuntimeException exception) {
            throw new DoclingExtractionException("Docling response was malformed", exception, false);
        }
    }

    private DocumentExtractionResult parse(String body) throws java.io.IOException {
        JsonNode root = mapper.readTree(body);
        if (!root.isObject()) throw new IllegalArgumentException("response must be an object");
        List<DocumentNode> nodes = new ArrayList<>();
        for (JsonNode node : root.path("nodes")) nodes.add(node(node));
        List<DocumentTable> tables = new ArrayList<>();
        for (JsonNode table : root.path("tables")) {
            List<List<String>> rows = new ArrayList<>();
            for (JsonNode row : table.path("rows")) {
                List<String> cells = new ArrayList<>();
                row.forEach(cell -> cells.add(cell.asText("")));
                rows.add(cells);
            }
            tables.add(new DocumentTable(text(table, "id"), positive(table, "page"), rows));
        }
        List<DocumentImage> images = new ArrayList<>();
        for (JsonNode image : root.path("images")) images.add(new DocumentImage(
                text(image, "id"), positive(image, "page"), box(image.path("bbox")),
                textOr(image, "mimeType", "application/octet-stream"), textOr(image, "caption", "")));
        List<ExtractionWarning> warnings = new ArrayList<>();
        for (JsonNode warning : root.path("warnings")) warnings.add(new ExtractionWarning(
                text(warning, "code"), ExtractionWarning.Severity.valueOf(textOr(warning, "severity", "WARNING")),
                text(warning, "message")));
        return new DocumentExtractionResult(nodes, tables, images, warnings, textOr(root, "rawText", ""));
    }

    private DocumentNode node(JsonNode value) {
        List<DocumentNode> children = new ArrayList<>();
        for (JsonNode child : value.path("children")) children.add(node(child));
        return new DocumentNode(text(value, "id"), DocumentNodeType.valueOf(textOr(value, "type", "UNKNOWN")),
                positive(value, "page"), value.has("bbox") ? box(value.path("bbox")) : null,
                textOr(value, "text", ""), children, List.of());
    }

    private static BoundingBox box(JsonNode value) {
        return new BoundingBox(value.path("left").asDouble(), value.path("top").asDouble(),
                value.path("right").asDouble(), value.path("bottom").asDouble());
    }
    private static String text(JsonNode value, String field) { String result = value.path(field).asText(""); if (result.isBlank()) throw new IllegalArgumentException(field + " is required"); return result; }
    private static String textOr(JsonNode value, String field, String fallback) { return value.path(field).asText(fallback); }
    private static int positive(JsonNode value, String field) { int result = value.path(field).asInt(0); if (result < 1) throw new IllegalArgumentException(field + " must be positive"); return result; }
    private record Request(String format, String contentBase64) {}
}
