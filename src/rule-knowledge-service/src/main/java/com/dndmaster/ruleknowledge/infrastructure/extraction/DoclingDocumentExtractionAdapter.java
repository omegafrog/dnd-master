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
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedDocument;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedElement;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedOutlineEntry;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedPage;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedParserRelation;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedPicture;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedSourceSpan;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedTable;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedWarning;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedDocumentPreservationValidator;
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
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.security.MessageDigest;

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
            return compatibilityProjection(parseNormalized(response.body(), sourceIdentity(content)));
        } catch (DoclingExtractionException exception) {
            throw exception;
        } catch (java.io.IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new DoclingExtractionException("Docling request failed", exception, true);
        } catch (RuntimeException exception) {
            throw new DoclingExtractionException("Docling response was malformed", exception, false);
        }
    }

    public NormalizedDocument extractNormalized(RulebookFormat format, byte[] content) {
        Objects.requireNonNull(format, "format must not be null");
        Objects.requireNonNull(content, "content must not be null");
        try {
            String body = mapper.writeValueAsString(new Request(format.name(), Base64.getEncoder().encodeToString(content)));
            HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 500 || response.statusCode() == 408 || response.statusCode() == 429)
                throw new DoclingExtractionException("Docling unavailable: HTTP " + response.statusCode(), true);
            if (response.statusCode() != 200)
                throw new DoclingExtractionException("Docling rejected document: HTTP " + response.statusCode(), false);
            return parseNormalized(response.body(), sourceIdentity(content));
        } catch (DoclingExtractionException exception) { throw exception;
        } catch (java.io.IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new DoclingExtractionException("Docling request failed", exception, true);
        } catch (RuntimeException exception) { throw new DoclingExtractionException("Docling response was malformed", exception, false); }
    }

    private NormalizedDocument parseNormalized(String body, String sourceIdentity) throws java.io.IOException {
        JsonNode root = mapper.readTree(body);
        if (!root.isObject()) throw new IllegalArgumentException("response must be an object");
        List<NormalizedPage> pages = new ArrayList<>();
        for (JsonNode page : root.path("pages")) pages.add(new NormalizedPage(positive(page, "number"), nullableDouble(page, "width"), nullableDouble(page, "height")));
        List<NormalizedElement> elements = new ArrayList<>();
        JsonNode elementRoot = root.has("elements") ? root.path("elements") : root.path("nodes");
        int[] order = {0};
        for (JsonNode node : elementRoot) addElements(node, null, elements, order);
        List<NormalizedTable> tables = new ArrayList<>();
        for (JsonNode table : root.path("tables")) {
            List<List<String>> rows = new ArrayList<>();
            for (JsonNode row : table.path("rows")) {
                List<String> cells = new ArrayList<>();
                row.forEach(cell -> cells.add(cell.asText("")));
                rows.add(cells);
            }
            tables.add(new NormalizedTable(text(table, "id"), positive(table, "page"), rows));
        }
        List<NormalizedPicture> pictures = new ArrayList<>();
        JsonNode pictureRoot = root.has("pictures") ? root.path("pictures") : root.path("images");
        for (JsonNode image : pictureRoot) pictures.add(new NormalizedPicture(text(image, "id"), positive(image, "page"), image.has("bbox") ? box(image.path("bbox")) : null,
                textOr(image, "mimeType", "application/octet-stream"), textOr(image, "caption", "")));
        List<NormalizedOutlineEntry> outlines = new ArrayList<>();
        for (JsonNode outline : root.path("outlines")) outlines.add(new NormalizedOutlineEntry(text(outline, "id"), textOr(outline, "title", ""), positive(outline, "level"), textOr(outline, "locator", "")));
        List<NormalizedParserRelation> relations = new ArrayList<>();
        for (JsonNode relation : root.path("parserRelations")) relations.add(new NormalizedParserRelation(text(relation, "childId"), text(relation, "parentId"), positive(relation, "level")));
        List<NormalizedWarning> warnings = new ArrayList<>();
        for (JsonNode warning : root.path("warnings")) warnings.add(new NormalizedWarning(text(warning, "code"), textOr(warning, "severity", "WARNING"), textOr(warning, "message", "")));
        NormalizedDocument result = new NormalizedDocument(textOr(root, "schemaVersion", "normalized-document.v1"), textOr(root, "extractor", "docling"),
                textOr(root, "extractorVersion", "unknown"), textOr(root, "sourceIdentity", sourceIdentity), pages, elements, tables, pictures, outlines, relations, warnings, textOr(root, "rawText", ""));
        NormalizedDocumentPreservationValidator.validate(result);
        return result;
    }

    private void addElements(JsonNode value, String parentId, List<NormalizedElement> result, int[] order) {
        String id = text(value, "id");
        int page = positive(value, "page");
        List<String> childIds = new ArrayList<>();
        value.path("children").forEach(child -> childIds.add(text(child, "id")));
        JsonNode span = value.path("sourceSpan");
        NormalizedSourceSpan sourceSpan = span.isObject() ? new NormalizedSourceSpan(textOr(span, "sourceId", id), positiveOr(span, "page", page), span.path("order").asInt(order[0]),
                nullableInteger(span, "start"), nullableInteger(span, "end"), span.has("bbox") ? box(span.path("bbox")) : null) :
                new NormalizedSourceSpan(id, page, order[0], null, null, value.has("bbox") ? box(value.path("bbox")) : null);
        result.add(new NormalizedElement(id, textOr(value, "type", "UNKNOWN"), textOr(value, "text", ""), page, order[0]++,
                textOr(value, "parentId", parentId), value.has("parserLevel") ? value.path("parserLevel").asInt() : null, childIds,
                sourceSpan, textOr(value, "style", ""), textOr(value, "layout", "")));
        value.path("children").forEach(child -> addElements(child, id, result, order));
    }

    private DocumentExtractionResult compatibilityProjection(NormalizedDocument document) {
        Map<String, List<NormalizedElement>> children = new HashMap<>();
        for (NormalizedElement element : document.elements()) children.computeIfAbsent(element.parentId(), ignored -> new ArrayList<>()).add(element);
        List<DocumentNode> roots = document.elements().stream().filter(element -> element.parentId() == null || !containsId(document.elements(), element.parentId()))
                .map(element -> toNode(element, children)).toList();
        List<DocumentTable> tables = document.tables().stream().map(table -> new DocumentTable(table.id(), table.page(), table.rows())).toList();
        List<DocumentImage> images = document.pictures().stream().map(picture -> new DocumentImage(picture.id(), picture.page(), picture.boundingBox(), picture.mimeType(), picture.caption())).toList();
        List<ExtractionWarning> warnings = document.warnings().stream().map(warning -> new ExtractionWarning(
                warning.code(), ExtractionWarning.Severity.valueOf(warning.severity()), warning.message())).toList();
        return new DocumentExtractionResult(roots, tables, images, warnings, document.rawText());
    }
    private static boolean containsId(List<NormalizedElement> elements, String id) { return elements.stream().anyMatch(element -> element.id().equals(id)); }
    private DocumentNode toNode(NormalizedElement element, Map<String, List<NormalizedElement>> children) {
        List<DocumentNode> nested = children.getOrDefault(element.id(), List.of()).stream().map(child -> toNode(child, children)).toList();
        DocumentNodeType type; try { type = DocumentNodeType.valueOf(element.type()); } catch (IllegalArgumentException ignored) { type = DocumentNodeType.UNKNOWN; }
        return new DocumentNode(element.id(), type, element.page(), element.sourceSpan().boundingBox(), element.text(), nested, List.of());
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
    private static int positiveOr(JsonNode value, String field, int fallback) { return value.has(field) ? positive(value, field) : fallback; }
    private static Integer nullableInteger(JsonNode value, String field) { return value.has(field) && !value.path(field).isNull() ? value.path(field).asInt() : null; }
    private static Double nullableDouble(JsonNode value, String field) { return value.has(field) && !value.path(field).isNull() ? value.path(field).asDouble() : null; }
    private static String sourceIdentity(byte[] content) {
        try { var digest = MessageDigest.getInstance("SHA-256"); return "sha256:" + HexFormat.of().formatHex(digest.digest(content)); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }
    private record Request(String format, String contentBase64) {}
}
