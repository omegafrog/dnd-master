package com.dndmaster.ruleknowledge.application.preprocessing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PreprocessingArtifactImporterTest {
    @Test
    void importsOnlyChunkProvenanceNeededByThePublicPublicationContract() throws Exception {
        Path root = Files.createTempDirectory("rag-017-import-");
        Path chunks = root.resolve("chunks.jsonl");
        Files.writeString(chunks, "{\"chunk_id\":\"chunk-1\",\"source_text\":\"A rule\",\"embedding_text\":\"A rule\",\"source_spans\":[{\"page_number\":2}],\"section_path\":[\"Combat\"]}\n");
        PreprocessingRunResult result = new PreprocessingRunResult(
                "retry-1", "candidate-2", "READY", "a".repeat(64), "rag-preprocessing-v1",
                List.of(new PreprocessingPageState(2, "VALIDATED", 2, List.of())),
                new PreprocessingArtifactManifest("b".repeat(64), Map.of("chunks", java.util.HexFormat.of().formatHex(
                        java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(chunks)))), Map.of("chunks", chunks)));

        var imported = new PreprocessingArtifactImporter(new ObjectMapper()).readChunks(result);

        assertEquals("chunk-1", imported.getFirst().processorChunkId());
        assertEquals(2, imported.getFirst().provenance().pageNumber());
        assertEquals(List.of("Combat"), imported.getFirst().provenance().sectionPath());
        assertEquals("page=2:chunk=chunk-1", imported.getFirst().provenance().originalLocator());
    }
}
