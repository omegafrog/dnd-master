package com.dndmaster.ruleknowledge.infrastructure.preprocessing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.ruleknowledge.application.preprocessing.PreprocessingProcessException;
import com.dndmaster.ruleknowledge.application.preprocessing.PreprocessingRunResult;
import com.dndmaster.ruleknowledge.application.preprocessing.PreprocessingRunRequest;
import com.dndmaster.ruleknowledge.application.preprocessing.PreprocessingStatusRequest;
import com.dndmaster.ruleknowledge.application.preprocessing.PreprocessingRetryRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class ProcessCliPreprocessingAdapterTest {
    @Test
    void callsPreprocessAndStatusContractsAndReturnsOnlySafeArtifactIdentity() throws Exception {
        Path work = Files.createTempDirectory("rag-014-adapter-");
        Path source = work.resolve("rules.md");
        Path output = work.resolve("artifacts");
        Files.writeString(source, "# Heading\nA native page", StandardCharsets.UTF_8);
        String hash = sha256(source);
        Map<String, Object> fixture = fixture(work, hash, "candidate-1", "adapter-1", "preprocess");
        Path preprocessScript = writeScript(work.resolve("fake-python-preprocess"), new ObjectMapper().writeValueAsString(fixture));
        ProcessCliPreprocessingAdapter adapter = new ProcessCliPreprocessingAdapter(
                preprocessScript.toString(), work, Duration.ofMinutes(2), new ObjectMapper());

        PreprocessingRunResult result = adapter.preprocess(
                new PreprocessingRunRequest("adapter-1", source, hash, "p1", output, "candidate-1"));

        assertEquals("adapter-1", result.requestId());
        assertEquals("candidate-1", result.versionId());
        assertEquals("READY", result.status(), result.pages().toString());
        assertEquals(hash, result.sourceSha256());
        assertFalse(result.artifacts().artifactSha256().values().stream().anyMatch(value -> value.contains("/")));
        assertFalse(result.artifacts().artifactSha256().keySet().stream().anyMatch(value -> value.contains("path")));

        Map<String, Object> statusFixture = fixture(work, hash, "candidate-1", "adapter-status", "status");
        Path statusScript = writeScript(work.resolve("fake-python-status"), new ObjectMapper().writeValueAsString(statusFixture));
        ProcessCliPreprocessingAdapter statusAdapter = new ProcessCliPreprocessingAdapter(
                statusScript.toString(), work, Duration.ofMinutes(2), new ObjectMapper());
        PreprocessingRunResult status = statusAdapter.status(
                new PreprocessingStatusRequest("adapter-status", result.versionId(), output));
        assertEquals("adapter-status", status.requestId());
        assertEquals("READY", status.status());
        assertEquals(hash, status.sourceSha256());
    }

    @Test
    void acceptsCachedPreprocessResponseReportedAsStatus() throws Exception {
        Path work = Files.createTempDirectory("rag-014-adapter-cached-preprocess-");
        Path source = work.resolve("rules.md");
        Files.writeString(source, "# Heading\nA native page", StandardCharsets.UTF_8);
        String hash = sha256(source);
        Map<String, Object> cachedFixture = fixture(work, hash, "candidate-cached", "adapter-cached", "status");
        Path script = writeScript(work.resolve("fake-python-cached-preprocess"),
                new ObjectMapper().writeValueAsString(cachedFixture));
        ProcessCliPreprocessingAdapter adapter = new ProcessCliPreprocessingAdapter(
                script.toString(), work, Duration.ofMinutes(2), new ObjectMapper());

        PreprocessingRunResult result = adapter.preprocess(
                new PreprocessingRunRequest("adapter-cached", source, hash, "p1", work.resolve("out"), null));

        assertEquals("candidate-cached", result.versionId());
        assertEquals("READY", result.status());
    }

    @Test
    void rejectsSourceHashMismatchWithoutExposingProcessDetails() throws Exception {
        Path work = Files.createTempDirectory("rag-014-adapter-");
        Path source = work.resolve("rules.txt");
        Files.writeString(source, "content", StandardCharsets.UTF_8);
        Map<String, Object> error = Map.of(
                "schema_version", "1", "operation", "preprocess", "request_id", "adapter-bad-hash",
                "error", Map.of("code", "SOURCE_HASH_MISMATCH", "message", "source hash mismatch"));
        Path script = writeScript(work.resolve("fake-python-error"), new ObjectMapper().writeValueAsString(error));
        ProcessCliPreprocessingAdapter adapter = new ProcessCliPreprocessingAdapter(
                script.toString(), work, Duration.ofMinutes(2), new ObjectMapper());

        PreprocessingProcessException exception = assertThrows(PreprocessingProcessException.class, () -> adapter.preprocess(
                new PreprocessingRunRequest("adapter-bad-hash", source, "0".repeat(64), "p1", work.resolve("out"), null)));

        assertEquals("SOURCE_HASH_MISMATCH", exception.code());
        assertFalse(exception.getMessage().contains(source.toString()));
    }

    @Test
    void drainsLargeProcessOutputBeforeWaitingForTheProcessToExit() throws Exception {
        Path work = Files.createTempDirectory("rag-014-adapter-large-output-");
        Path source = work.resolve("rules.txt");
        Files.writeString(source, "content", StandardCharsets.UTF_8);
        String hash = sha256(source);
        Map<String, Object> fixture = fixture(work, hash, "candidate-large", "adapter-large", "preprocess");
        fixture.put("diagnostic", "x".repeat(128 * 1024));
        Path script = writeScript(work.resolve("fake-python-large-output"), new ObjectMapper().writeValueAsString(fixture));
        ProcessCliPreprocessingAdapter adapter = new ProcessCliPreprocessingAdapter(
                script.toString(), work, Duration.ofSeconds(3), new ObjectMapper());

        PreprocessingRunResult result = adapter.preprocess(
                new PreprocessingRunRequest("adapter-large", source, hash, "p1", work.resolve("out"), "candidate-large"));

        assertEquals("candidate-large", result.versionId());
    }

    @Test
    void rejectsUnknownArtifactManifestEntries() throws Exception {
        Path work = Files.createTempDirectory("rag-014-adapter-");
        Path unexpected = work.resolve("unexpected.json");
        Files.writeString(unexpected, "{}\n", StandardCharsets.UTF_8);
        Map<String, Object> fixture = fixture(work, "a".repeat(64), "candidate-1", "adapter-unknown-artifact", "preprocess");
        @SuppressWarnings("unchecked")
        Map<String, Object> artifacts = (Map<String, Object>) fixture.get("artifacts");
        artifacts.put("unexpected", Map.of("path", unexpected.toString(), "sha256", sha256(unexpected)));
        Path script = writeScript(work.resolve("fake-python-unknown-artifact"), new ObjectMapper().writeValueAsString(fixture));
        ProcessCliPreprocessingAdapter adapter = new ProcessCliPreprocessingAdapter(
                script.toString(), work, Duration.ofMinutes(2), new ObjectMapper());

        PreprocessingProcessException exception = assertThrows(PreprocessingProcessException.class, () -> adapter.preprocess(
                new PreprocessingRunRequest("adapter-unknown-artifact", work.resolve("source.pdf"), "a".repeat(64), "p1", work.resolve("out"), "candidate-1")));

        assertEquals("ARTIFACT_MANIFEST_MISMATCH", exception.code());
    }

    @Test
    void acceptsSuccessfulRetryPromotionWithAFreshVersionId() throws Exception {
        Path work = Files.createTempDirectory("rag-017-adapter-");
        String hash = "a".repeat(64);
        Map<String, Object> fixture = fixture(work, hash, "candidate-1-retry", "retry-request", "retry_pages");
        fixture.put("retry_version_id", "candidate-1");
        Path script = writeScript(work.resolve("fake-python-retry"), new ObjectMapper().writeValueAsString(fixture));
        ProcessCliPreprocessingAdapter adapter = new ProcessCliPreprocessingAdapter(
                script.toString(), work, Duration.ofMinutes(2), new ObjectMapper());

        PreprocessingRunResult result = adapter.retryPages(new PreprocessingRetryRequest(
                "retry-request", "candidate-1", work, List.of(2)));

        assertEquals("candidate-1-retry", result.versionId());
        assertEquals("READY", result.status());
    }

    private static String sha256(Path path) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static Map<String, Object> fixture(Path work, String sourceHash, String versionId, String requestId, String operation)
            throws Exception {
        Map<String, Object> artifacts = new LinkedHashMap<>();
        Map<String, Object> files = new LinkedHashMap<>();
        for (String name : new String[]{"manifest", "version", "chunks", "document_tree", "issues"}) {
            Path file = work.resolve(name + ("chunks".equals(name) || "issues".equals(name) ? ".jsonl" : ".json"));
            Files.writeString(file, "{}\n", StandardCharsets.UTF_8);
            files.put(name, Map.of("path", file.toString(), "sha256", sha256(file)));
        }
        Map<String, Object> manifest = Map.of(
                "source_sha256", sourceHash,
                "source", Map.of("sha256", sourceHash),
                "pipeline_version", "p1",
                "policy", Map.of("version", "p1"));
        Path manifestPath = work.resolve("manifest.json");
        Files.writeString(manifestPath, new ObjectMapper().writeValueAsString(manifest), StandardCharsets.UTF_8);
        files.put("manifest", Map.of("path", manifestPath.toString(), "sha256", sha256(manifestPath)));
        Map<String, Object> version = Map.of("version_id", versionId, "source_sha256", sourceHash, "status", "READY", "page_count", 1);
        Path versionPath = work.resolve("version.json");
        Files.writeString(versionPath, new ObjectMapper().writeValueAsString(version), StandardCharsets.UTF_8);
        files.put("version", Map.of("path", versionPath.toString(), "sha256", sha256(versionPath)));
        Map<String, Object> page = Map.of("page_number", 1, "status", "VALIDATED", "attempts", 1, "findings", List.of());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("schema_version", "1"); response.put("operation", operation); response.put("request_id", requestId);
        response.put("version_id", versionId); response.put("status", "READY"); response.put("pages", List.of(page));
        response.put("page_summary", Map.of("count", 1, "processed", 1, "validated", 1, "needs_review", 0, "ready", 1));
        response.put("artifacts", files); response.put("manifest_sha256", files.get("manifest"));
        Map<String, Object> refs = new LinkedHashMap<>((Map<String, Object>) files);
        refs.put("manifest_sha256", ((Map<String, Object>) files.get("manifest")).get("sha256"));
        response.put("artifacts", refs); response.put("manifest", manifest);
        return response;
    }

    private static Path writeScript(Path path, String json) throws Exception {
        Files.writeString(path, "#!/bin/sh\nread request\nprintf '%s' '" + json + "'\n", StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(path, java.util.Set.of(
                java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE));
        return path;
    }
}
