package com.dndmaster.ruleknowledge.infrastructure.extraction;

import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Runs the repository's Docling/LlamaIndex structure-aware indexer for PDF extraction.
 * The legacy extractor is retained only as an availability fallback when Docling is unavailable. */
public final class DoclingPdfRulebookContentExtractor implements CompositeRulebookContentExtractor.FormatExtractor {
    private final String pythonExecutable;
    private final Path workingDirectory;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final CompositeRulebookContentExtractor.FormatExtractor fallback;

    public DoclingPdfRulebookContentExtractor(String pythonExecutable, Path workingDirectory, Duration timeout,
            ObjectMapper mapper, CompositeRulebookContentExtractor.FormatExtractor fallback) {
        this.pythonExecutable = Objects.requireNonNull(pythonExecutable);
        this.workingDirectory = Objects.requireNonNull(workingDirectory);
        this.timeout = Objects.requireNonNull(timeout);
        this.mapper = Objects.requireNonNull(mapper);
        this.fallback = Objects.requireNonNull(fallback);
    }

    @Override public ExtractionResult extract(byte[] content) {
        Path input = null;
        Path output = null;
        try {
            input = Files.createTempFile("dnd-rulebook-", ".pdf");
            output = Files.createTempFile("dnd-docling-", ".json");
            Files.write(input, content);
            Process process = new ProcessBuilder(pythonExecutable, "-m", "tools.storybook_indexing.indexer",
                    input.toString(), "--output", output.toString())
                    .directory(workingDirectory.toFile()).redirectErrorStream(true).start();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS) || process.exitValue() != 0) {
                process.destroyForcibly();
                return fallback.extract(content);
            }
            JsonNode root = mapper.readTree(Files.readString(output));
            StringBuilder text = new StringBuilder();
            for (JsonNode chunk : root.path("chunks")) {
                String value = chunk.path("contextual_content").asText();
                if (!value.isBlank()) {
                    if (!text.isEmpty()) text.append("\n\n");
                    text.append(value);
                }
            }
            return text.isEmpty() ? fallback.extract(content) : ExtractionResult.success(text.toString());
        } catch (Exception ignored) {
            return fallback.extract(content);
        } finally {
            deleteQuietly(input);
            deleteQuietly(output);
        }
    }

    private static void deleteQuietly(Path file) {
        if (file == null) return;
        try { Files.deleteIfExists(file); } catch (java.io.IOException ignored) { }
    }
}
