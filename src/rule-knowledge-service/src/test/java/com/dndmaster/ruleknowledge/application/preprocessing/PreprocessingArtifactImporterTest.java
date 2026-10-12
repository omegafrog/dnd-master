package com.dndmaster.ruleknowledge.application.preprocessing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class PreprocessingArtifactImporterTest {
    @Test
    void rejectsChunkArtifactWithContentOverCharacterLimit() throws Exception {
        Path root = Files.createTempDirectory("rag-oversized-import-");
        Path chunks = root.resolve("chunks.jsonl");
        Files.writeString(chunks, "{\"chunk_id\":\"chunk-1\",\"canonical_key\":\"combat.attack\",\"content_type\":\"rule\",\"token_count\":2,\"source_text\":\"" + "x".repeat(6001)
                + "\",\"embedding_text\":\"short\",\"source_spans\":[{\"page_number\":1}]}\n");

        assertThrows(IllegalArgumentException.class,
                () -> new PreprocessingArtifactImporter(new ObjectMapper()).readChunks(resultFor(chunks)));
    }

    @Test
    void countsSupplementaryCharactersLikePythonCharacterLength() throws Exception {
        Path root = Files.createTempDirectory("rag-unicode-import-");
        Path chunks = root.resolve("chunks.jsonl");
        String text = "😀".repeat(3001);
        Files.writeString(chunks, "{\"chunk_id\":\"chunk-1\",\"canonical_key\":\"combat.attack\",\"content_type\":\"rule\",\"token_count\":2,\"source_text\":\"" + text
                + "\",\"embedding_text\":\"" + text + "\",\"source_spans\":[{\"page_number\":1}]}\n");

        assertEquals(1, new PreprocessingArtifactImporter(new ObjectMapper()).readChunks(resultFor(chunks)).size());
    }

    @Test
    void importsAllChunkMetadataAndLogsCounts() throws Exception {
        Path root = Files.createTempDirectory("rag-017-import-");
        Path chunks = root.resolve("chunks.jsonl");
        Files.writeString(chunks, "{\"chunk_id\":\"chunk-1\",\"canonical_key\":\"combat.attack\",\"content_type\":\"rule\",\"token_count\":2,\"source_text\":\"A rule\",\"embedding_text\":\"A rule\",\"source_spans\":["
                + "{\"page_number\":2,\"block_index\":4,\"char_start\":120,\"char_end\":126,\"bbox\":[1,2,30,40]},"
                + "{\"page_number\":3,\"block_index\":1,\"char_start\":4,\"char_end\":10,\"bbox\":[2,3,31,41],\"table_id\":\"p3-table-0\",\"table_cell\":\"r1:c2\"}],"
                + "\"section_path\":[\"Combat\"]}\n");
        PreprocessingRunResult result = new PreprocessingRunResult(
                "retry-1", "candidate-2", "READY", "a".repeat(64), "rag-preprocessing-v1",
                List.of(new PreprocessingPageState(2, "VALIDATED", 2, List.of())),
                new PreprocessingArtifactManifest("b".repeat(64), Map.of("chunks", java.util.HexFormat.of().formatHex(
                        java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(chunks)))), Map.of("chunks", chunks)));

        String previous = System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
        System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true");
        Logger logger = Logger.getLogger(PreprocessingArtifactImporter.class.getName());
        List<String> logMessages = new java.util.ArrayList<>();
        Handler handler = new Handler() {
            @Override public void publish(LogRecord record) { logMessages.add(record.getMessage()); }
            @Override public void flush() {}
            @Override public void close() {}
        };
        handler.setLevel(Level.INFO);
        logger.addHandler(handler);
        List<com.dndmaster.ruleknowledge.application.publication.PublishedRagChunk> imported;
        try { imported = new PreprocessingArtifactImporter(new ObjectMapper()).readChunks(result); }
        finally {
            logger.removeHandler(handler);
            if (previous == null) System.clearProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
            else System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", previous);
        }

        assertEquals("chunk-1", imported.getFirst().processorChunkId());
        assertEquals("combat.attack", imported.getFirst().canonicalKey());
        assertEquals("rule", imported.getFirst().contentType());
        assertEquals(2, imported.getFirst().tokenCount());
        assertEquals(2, imported.getFirst().provenance().pageNumber());
        assertEquals(List.of("Combat"), imported.getFirst().provenance().sectionPath());
        assertEquals("page=2:chunk=chunk-1", imported.getFirst().provenance().originalLocator());
        assertEquals(List.of(1d, 2d, 30d, 40d), imported.getFirst().provenance().bbox());
        assertEquals(2, imported.getFirst().provenance().sourceSpans().size());
        assertEquals(3, imported.getFirst().provenance().sourceSpans().get(1).pageNumber());
        assertEquals(4, imported.getFirst().provenance().sourceSpans().getFirst().blockIndex());
        assertEquals(120, imported.getFirst().provenance().sourceSpans().getFirst().charStart());
        assertEquals("p3-table-0", imported.getFirst().provenance().sourceSpans().get(1).tableId());
        assertEquals("r1:c2", imported.getFirst().provenance().sourceSpans().get(1).tableCell());
        assertTrue(logMessages.stream().anyMatch(message -> message.contains(
                "stage=complete chunkCount=1 sourceSpanCount=2 maxTokenCount=2 maxContentCharacters=6 metadataFields=content_type,token_count,canonical_key")));
    }

    @Test
    void preservesParentKeyForChunksSplitFromTheSameScene() throws Exception {
        Path root = Files.createTempDirectory("rag-scene-parent-");
        Path chunks = root.resolve("chunks.jsonl");
        Files.writeString(chunks,
                "{\"chunk_id\":\"chunk-1\",\"canonical_key\":\"scene.stone_bridge.part-001\",\"content_type\":\"narrative\",\"token_count\":1,\"source_text\":\"first\",\"embedding_text\":\"first\",\"source_spans\":[{\"page_number\":2}],\"section_path\":[\"1. Stone Bridge\"],\"parent_key\":\"scene.stone_bridge\"}\n"
                        + "{\"chunk_id\":\"chunk-2\",\"canonical_key\":\"scene.stone_bridge.part-002\",\"content_type\":\"narrative\",\"token_count\":1,\"source_text\":\"second\",\"embedding_text\":\"second\",\"source_spans\":[{\"page_number\":2}],\"section_path\":[\"1. Stone Bridge\"],\"parent_key\":\"scene.stone_bridge\"}\n");
        PreprocessingRunResult result = resultFor(chunks);

        var imported = new PreprocessingArtifactImporter(new ObjectMapper()).readChunks(result);

        assertEquals(List.of("scene.stone_bridge", "scene.stone_bridge"),
                imported.stream().map(com.dndmaster.ruleknowledge.application.publication.PublishedRagChunk::parentKey).toList());
    }

    private static PreprocessingRunResult resultFor(Path chunks) throws Exception {
        return new PreprocessingRunResult(
                "retry-1", "candidate-2", "READY", "a".repeat(64), "rag-preprocessing-v1",
                List.of(new PreprocessingPageState(2, "VALIDATED", 2, List.of())),
                new PreprocessingArtifactManifest("b".repeat(64), Map.of("chunks", java.util.HexFormat.of().formatHex(
                        java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(chunks)))), Map.of("chunks", chunks)));
    }
}
