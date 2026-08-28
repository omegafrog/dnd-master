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

/** Imports only the validated, process-owned chunk artifact into the publication boundary. */
public final class PreprocessingArtifactImporter {
    private final ObjectMapper objectMapper;

    public PreprocessingArtifactImporter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<PublishedRagChunk> readChunks(PreprocessingRunResult result) {
        Path path = result.artifacts().artifactPath("chunks");
        if (path == null) {
            throw new IllegalArgumentException("CHUNK_ARTIFACT_MISSING");
        }
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path)
                || path.getParent() == null || containsSymlink(path)
                || !result.artifacts().artifactSha256().getOrDefault("chunks", "").equals(sha256(path))) {
            throw new IllegalArgumentException("CHUNK_ARTIFACT_MISSING");
        }
        try {
            List<PublishedRagChunk> chunks = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            int sequence = 0;
            for (String line : Files.readAllLines(path)) {
                if (line.isBlank()) continue;
                JsonNode chunk = objectMapper.readTree(line);
                String processorId = text(chunk, "chunk_id");
                String content = text(chunk, "source_text");
                String embeddingText = text(chunk, "embedding_text");
                if (!ids.add(processorId)) throw new IllegalArgumentException("DUPLICATE_PROCESSOR_CHUNK_ID");
                JsonNode spans = chunk.path("source_spans");
                if (!spans.isArray() || spans.isEmpty()) throw new IllegalArgumentException("CHUNK_SOURCE_SPAN_MISSING");
                JsonNode firstSpan = spans.get(0);
                int page = firstSpan.path("page_number").asInt(0);
                if (page < 1) throw new IllegalArgumentException("CHUNK_SOURCE_SPAN_INVALID");
                List<String> sectionPath = new ArrayList<>();
                JsonNode sections = chunk.path("section_path");
                if (sections.isArray()) sections.forEach(item -> { if (item.isTextual() && !item.asText().isBlank()) sectionPath.add(item.asText()); });
                String locator = "page=" + page + ":chunk=" + processorId;
                chunks.add(new PublishedRagChunk(processorId, sequence++, content, embeddingText,
                        new SourceProvenance(page, sectionPath, List.of(), null, locator)));
            }
            if (chunks.isEmpty()) throw new IllegalArgumentException("CHUNK_ARTIFACT_EMPTY");
            return List.copyOf(chunks);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegal) throw illegal;
            throw new IllegalArgumentException("CHUNK_ARTIFACT_INVALID", exception);
        }
    }

    private static String text(JsonNode node, String name) {
        String value = node.path(name).asText("");
        if (value.isBlank()) throw new IllegalArgumentException("CHUNK_ARTIFACT_INVALID");
        return value;
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
}
