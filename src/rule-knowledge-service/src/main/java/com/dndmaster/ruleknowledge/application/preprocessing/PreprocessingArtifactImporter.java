package com.dndmaster.ruleknowledge.application.preprocessing;

import com.dndmaster.ruleknowledge.application.publication.PublishedRagChunk;
import com.dndmaster.ruleknowledge.application.publication.SourceProvenance;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import com.dndmaster.ruleknowledge.application.publication.SourceProvenance.SourceSpan;

/** Imports only the validated, process-owned chunk artifact into the publication boundary. */
public final class PreprocessingArtifactImporter {
    private static final int MAX_CHUNK_CHARACTERS = 6000;
    private static final Logger LOGGER = Logger.getLogger(PreprocessingArtifactImporter.class.getName());
    private final ObjectMapper objectMapper;

    public PreprocessingArtifactImporter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<PublishedRagChunk> readChunks(PreprocessingRunResult result) {
        long started = System.nanoTime();
        log("dev_preprocessing_artifact_import stage=start artifact=chunks outcome=started");
        try {
            Path path = result.artifacts().artifactPath("chunks");
            if (path == null) throw new IllegalArgumentException("CHUNK_ARTIFACT_MISSING");
            if (Files.isSymbolicLink(path) || !Files.isRegularFile(path)
                    || path.getParent() == null || containsSymlink(path)
                    || !result.artifacts().artifactSha256().getOrDefault("chunks", "").equals(sha256(path))) {
                throw new IllegalArgumentException("CHUNK_ARTIFACT_MISSING");
            }
            List<PublishedRagChunk> chunks = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            int sequence = 0;
            int sourceSpanCount = 0;
            int maxTokenCount = 0;
            int maxContentCharacters = 0;
            for (String line : Files.readAllLines(path)) {
                if (line.isBlank()) continue;
                JsonNode chunk = objectMapper.readTree(line);
                String processorId = text(chunk, "chunk_id");
                String content = text(chunk, "source_text");
                String embeddingText = text(chunk, "embedding_text");
                if (content.codePointCount(0, content.length()) > MAX_CHUNK_CHARACTERS
                        || embeddingText.codePointCount(0, embeddingText.length()) > MAX_CHUNK_CHARACTERS) {
                    throw new IllegalArgumentException("CHUNK_CHARACTER_LIMIT_EXCEEDED");
                }
                String contentType = text(chunk, "content_type");
                String canonicalKey = text(chunk, "canonical_key");
                int tokenCount = requiredPositiveInt(chunk, "token_count");
                if (!ids.add(processorId)) throw new IllegalArgumentException("DUPLICATE_PROCESSOR_CHUNK_ID");
                JsonNode spans = chunk.path("source_spans");
                if (!spans.isArray() || spans.isEmpty()) throw new IllegalArgumentException("CHUNK_SOURCE_SPAN_MISSING");
                List<SourceSpan> sourceSpans = new ArrayList<>(spans.size());
                for (JsonNode span : spans) sourceSpans.add(readSourceSpan(span));
                sourceSpanCount += sourceSpans.size();
                maxTokenCount = Math.max(maxTokenCount, tokenCount);
                maxContentCharacters = Math.max(maxContentCharacters, content.length());
                SourceSpan firstSpan = sourceSpans.getFirst();
                List<String> sectionPath = new ArrayList<>();
                JsonNode sections = chunk.path("section_path");
                if (sections.isArray()) sections.forEach(item -> { if (item.isTextual() && !item.asText().isBlank()) sectionPath.add(item.asText()); });
                String locator = "page=" + firstSpan.pageNumber() + ":chunk=" + processorId;
                chunks.add(new PublishedRagChunk(processorId, sequence++, content, embeddingText,
                        contentType, tokenCount, canonicalKey,
                        new SourceProvenance(firstSpan.pageNumber(), sectionPath, firstSpan.bbox(), firstSpan.tableCell(), locator, sourceSpans), optionalText(chunk, "parent_key")));
            }
            if (chunks.isEmpty()) throw new IllegalArgumentException("CHUNK_ARTIFACT_EMPTY");
            log("dev_preprocessing_artifact_import stage=complete chunkCount=" + chunks.size()
                    + " sourceSpanCount=" + sourceSpanCount + " maxTokenCount=" + maxTokenCount
                    + " maxContentCharacters=" + maxContentCharacters + " metadataFields=content_type,token_count,canonical_key"
                    + " elapsedMs=" + elapsedMs(started) + " outcome=completed");
            return List.copyOf(chunks);
        } catch (IOException | RuntimeException exception) {
            log("dev_preprocessing_artifact_import stage=failed failureType=" + exception.getClass().getSimpleName()
                    + " failureCode=" + (exception.getMessage() == null ? "unknown" : exception.getMessage())
                    + " elapsedMs=" + elapsedMs(started) + " recoveryAction=reject_artifact outcome=failed");
            if (exception instanceof IllegalArgumentException illegal) throw illegal;
            throw new IllegalArgumentException("CHUNK_ARTIFACT_INVALID", exception);
        }
    }

    private static SourceSpan readSourceSpan(JsonNode span) {
        int page = requiredInt(span, "page_number");
        List<Double> bbox = new ArrayList<>();
        JsonNode rawBbox = span.path("bbox");
        if (!rawBbox.isMissingNode() && !rawBbox.isNull()) {
            if (!rawBbox.isArray() || rawBbox.size() != 4) throw new IllegalArgumentException("CHUNK_SOURCE_SPAN_INVALID");
            rawBbox.forEach(value -> {
                if (!value.isNumber() || !Double.isFinite(value.asDouble())) throw new IllegalArgumentException("CHUNK_SOURCE_SPAN_INVALID");
                bbox.add(value.asDouble());
            });
        }
        return new SourceSpan(page, optionalInt(span, "block_index"), optionalInt(span, "char_start"),
                optionalInt(span, "char_end"), optionalInt(span, "token_start"), optionalInt(span, "token_end"),
                bbox, optionalText(span, "table_id"), optionalText(span, "table_cell"));
    }

    private static int requiredInt(JsonNode node, String name) {
        Integer value = optionalInt(node, name);
        if (value == null || value < 1) throw new IllegalArgumentException("CHUNK_SOURCE_SPAN_INVALID");
        return value;
    }

    private static Integer optionalInt(JsonNode node, String name) {
        JsonNode value = node.path(name);
        if (value.isMissingNode() || value.isNull()) return null;
        if (!value.isIntegralNumber() || !value.canConvertToInt()) throw new IllegalArgumentException("CHUNK_SOURCE_SPAN_INVALID");
        return value.intValue();
    }

    private static int requiredPositiveInt(JsonNode node, String name) {
        Integer value = optionalInt(node, name);
        if (value == null || value <= 0) throw new IllegalArgumentException("CHUNK_METADATA_INVALID");
        return value;
    }

    private static String text(JsonNode node, String name) {
        String value = node.path(name).asText("");
        if (value.isBlank()) throw new IllegalArgumentException("CHUNK_ARTIFACT_INVALID");
        return value;
    }

    private static String optionalText(JsonNode node, String name) {
        String value = node.path(name).asText("");
        return value.isBlank() ? null : value;
    }

    private static String sha256(Path path) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(path)));
        } catch (Exception exception) {
            throw new IllegalArgumentException("CHUNK_ARTIFACT_INVALID", exception);
        }
    }

    private static boolean containsSymlink(Path path) {
        for (Path current = path; current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current)) return true;
        }
        return false;
    }

    private static void log(String message) {
        if (Boolean.parseBoolean(System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED",
                System.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED")))) LOGGER.info(message);
    }

    private static long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
