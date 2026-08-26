package com.dndmaster.ruleknowledge.infrastructure.preprocessing;

import com.dndmaster.ruleknowledge.application.preprocessing.PreprocessingArtifactManifest;
import com.dndmaster.ruleknowledge.application.preprocessing.PreprocessingPageState;
import com.dndmaster.ruleknowledge.application.preprocessing.PreprocessingProcessException;
import com.dndmaster.ruleknowledge.application.preprocessing.PreprocessingProcessPort;
import com.dndmaster.ruleknowledge.application.preprocessing.PreprocessingRetryRequest;
import com.dndmaster.ruleknowledge.application.preprocessing.PreprocessingRunRequest;
import com.dndmaster.ruleknowledge.application.preprocessing.PreprocessingRunResult;
import com.dndmaster.ruleknowledge.application.preprocessing.PreprocessingStatusRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Invokes the versioned Python stdin/stdout process contract without leaking process paths. */
public final class ProcessCliPreprocessingAdapter implements PreprocessingProcessPort {
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern VERSION_ID = Pattern.compile("[A-Za-z0-9._-]+");
    private static final String MODULE = "preprocessing_agent.adapters.process_cli";
    private static final Set<String> ARTIFACT_KEYS = Set.of(
            "manifest_sha256", "manifest", "version", "chunks", "document_tree", "issues");

    private final String pythonExecutable;
    private final Path workingDirectory;
    private final Duration timeout;
    private final ObjectMapper objectMapper;

    public ProcessCliPreprocessingAdapter(
            String pythonExecutable, Path workingDirectory, Duration timeout, ObjectMapper objectMapper) {
        this.pythonExecutable = requireText(pythonExecutable, "pythonExecutable");
        this.workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory must not be null")
                .toAbsolutePath().normalize();
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public PreprocessingRunResult preprocess(PreprocessingRunRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schema_version", "1");
        payload.put("operation", "preprocess");
        payload.put("request_id", request.requestId());
        payload.put("source_path", request.sourcePath().toAbsolutePath().normalize().toString());
        payload.put("source_sha256", request.sourceSha256());
        payload.put("policy_version", request.policyVersion());
        payload.put("output_dir", request.outputDir().toAbsolutePath().normalize().toString());
        if (request.versionId() != null) {
            payload.put("version_id", request.versionId());
        }
        return invoke(payload, "preprocess", request.requestId(), request.sourceSha256(), request.policyVersion(), null);
    }

    @Override
    public PreprocessingRunResult status(PreprocessingStatusRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return invoke(Map.of(
                "schema_version", "1",
                "operation", "status",
                "request_id", request.requestId(),
                "version_id", request.versionId(),
                "artifact_root", request.artifactRoot().toAbsolutePath().normalize().toString()),
                "status", request.requestId(), null, null, request.versionId());
    }

    @Override
    public PreprocessingRunResult retryPages(PreprocessingRetryRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return invoke(Map.of(
                "schema_version", "1",
                "operation", "retry_pages",
                "request_id", request.requestId(),
                "version_id", request.versionId(),
                "artifact_root", request.artifactRoot().toAbsolutePath().normalize().toString(),
                "pages", request.pages()),
                "retry_pages", request.requestId(), null, null, request.versionId());
    }

    private PreprocessingRunResult invoke(
            Map<String, Object> request,
            String expectedOperation,
            String expectedRequestId,
            String expectedSourceHash,
            String expectedPolicyVersion,
            String expectedVersionId) {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(pythonExecutable, "-m", MODULE)
                    .directory(workingDirectory.toFile())
                    .redirectErrorStream(false);
            Path sourceRoot = workingDirectory.resolve("src");
            if (Files.isDirectory(sourceRoot)) {
                String existingPythonPath = builder.environment().get("PYTHONPATH");
                builder.environment().put("PYTHONPATH", sourceRoot + (existingPythonPath == null ? "" : java.io.File.pathSeparator + existingPythonPath));
            }
            process = builder.start();
            String requestJson = objectMapper.writeValueAsString(request);
            process.getOutputStream().write(requestJson.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().close();
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new PreprocessingProcessException("PREPROCESSING_TIMEOUT");
            }
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            // stderr is deliberately consumed but never included in a user-facing exception or response.
            process.getErrorStream().readAllBytes();
            JsonNode response = stdout.isBlank() ? null : objectMapper.readTree(stdout);
            if (response == null || !response.isObject()) {
                throw new PreprocessingProcessException("MALFORMED_PROCESS_RESPONSE");
            }
            JsonNode error = response.get("error");
            if (error != null && !error.isNull()) {
                throw new PreprocessingProcessException(safeCode(error.path("code").asText("PROCESSING_FAILED")));
            }
            if (process.exitValue() != 0) {
                throw new PreprocessingProcessException("PREPROCESSING_FAILED");
            }
            return parseResponse(response, expectedOperation, expectedRequestId, expectedSourceHash, expectedPolicyVersion, expectedVersionId);
        } catch (PreprocessingProcessException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PreprocessingProcessException("PREPROCESSING_INTERRUPTED", exception);
        } catch (IOException | RuntimeException exception) {
            throw new PreprocessingProcessException("PREPROCESSING_PROCESS_UNAVAILABLE", exception);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private PreprocessingRunResult parseResponse(
            JsonNode response,
            String expectedOperation,
            String expectedRequestId,
            String expectedSourceHash,
            String expectedPolicyVersion,
            String expectedVersionId) {
        if (!"1".equals(response.path("schema_version").asText())
                || !expectedOperation.equals(response.path("operation").asText())
                || !expectedRequestId.equals(response.path("request_id").asText())) {
            throw new PreprocessingProcessException("PROCESS_CORRELATION_MISMATCH");
        }
        String versionId = response.path("version_id").asText("");
        if (!VERSION_ID.matcher(versionId).matches() || ".".equals(versionId) || "..".equals(versionId)
                || (expectedVersionId != null && !expectedVersionId.equals(versionId))) {
            throw new PreprocessingProcessException("VERSION_ID_MISMATCH");
        }
        String status = response.path("status").asText("");
        if (!List.of("QUEUED", "PROCESSING", "VALIDATING", "READY", "NEEDS_REVIEW").contains(status)) {
            throw new PreprocessingProcessException("INVALID_PREPROCESSING_STATUS");
        }
        List<PreprocessingPageState> pages = parsePages(response.path("pages"));
        validatePageSummary(response.path("page_summary"), pages);
        JsonNode artifacts = response.path("artifacts");
        if (!artifacts.isObject()) {
            throw new PreprocessingProcessException("ARTIFACT_MANIFEST_MISMATCH");
        }
        Map<String, String> hashes = validateArtifacts(artifacts, versionId, status);
        String manifestHash = text(artifacts, "manifest_sha256");
        if (!SHA256.matcher(manifestHash).matches() || !manifestHash.equals(hashes.get("manifest"))) {
            throw new PreprocessingProcessException("ARTIFACT_MANIFEST_MISMATCH");
        }
        JsonNode manifest = readArtifactJson(artifacts.path("manifest"));
        String sourceHash = manifest.path("source_sha256").asText(
                manifest.path("source").path("sha256").asText(""));
        if (!SHA256.matcher(sourceHash).matches()
                || (expectedSourceHash != null && !expectedSourceHash.equals(sourceHash))) {
            throw new PreprocessingProcessException("SOURCE_HASH_MISMATCH");
        }
        String policyVersion = manifest.path("policy").path("version").asText(
                manifest.path("pipeline_version").asText("unknown"));
        if (expectedPolicyVersion != null && !expectedPolicyVersion.equals(policyVersion)) {
            throw new PreprocessingProcessException("POLICY_VERSION_MISMATCH");
        }
        JsonNode versionArtifact = readArtifactJson(artifacts.path("version"));
        if (!versionId.equals(versionArtifact.path("version_id").asText())
                || !sourceHash.equals(versionArtifact.path("source_sha256").asText())
                || !status.equals(versionArtifact.path("status").asText())
                || versionArtifact.path("page_count").asInt(-1) != pages.size()) {
            throw new PreprocessingProcessException("VERSION_MANIFEST_MISMATCH");
        }
        if (!response.path("manifest").equals(manifest)) {
            throw new PreprocessingProcessException("ARTIFACT_MANIFEST_MISMATCH");
        }
        return new PreprocessingRunResult(
                expectedRequestId,
                versionId,
                status,
                sourceHash,
                policyVersion,
                pages,
                new PreprocessingArtifactManifest(manifestHash, hashes));
    }

    private List<PreprocessingPageState> parsePages(JsonNode pagesNode) {
        if (!pagesNode.isArray() || pagesNode.isEmpty()) {
            throw new PreprocessingProcessException("PAGE_MANIFEST_MISMATCH");
        }
        List<PreprocessingPageState> pages = new ArrayList<>();
        int expectedNumber = 1;
        for (JsonNode page : pagesNode) {
            int number = page.path("page_number").asInt(0);
            int attempts = page.path("attempts").asInt(0);
            if (number != expectedNumber || attempts < 1) {
                throw new PreprocessingProcessException("PAGE_MANIFEST_MISMATCH");
            }
            List<String> findings = new ArrayList<>();
            JsonNode findingsNode = page.path("findings");
            if (!findingsNode.isArray()) {
                throw new PreprocessingProcessException("PAGE_DIAGNOSTIC_MISMATCH");
            }
            findingsNode.forEach(item -> {
                if (!item.isTextual()) {
                    throw new PreprocessingProcessException("PAGE_DIAGNOSTIC_MISMATCH");
                }
                findings.add(item.asText());
            });
            pages.add(new PreprocessingPageState(number, page.path("status").asText(""), attempts, findings));
            expectedNumber++;
        }
        return List.copyOf(pages);
    }

    private static void validatePageSummary(JsonNode summary, List<PreprocessingPageState> pages) {
        if (!summary.isObject() || summary.path("count").asInt(-1) != pages.size()
                || summary.path("processed").asInt(-1) != pages.size()) {
            throw new PreprocessingProcessException("PAGE_MANIFEST_MISMATCH");
        }
        long validated = pages.stream().filter(page -> "VALIDATED".equals(page.status())).count();
        long review = pages.stream().filter(page -> "NEEDS_REVIEW".equals(page.status())).count();
        if (summary.path("validated").asLong(-1) != validated
                || summary.path("needs_review").asLong(-1) != review
                || summary.path("ready").asLong(-1) != validated) {
            throw new PreprocessingProcessException("PAGE_MANIFEST_MISMATCH");
        }
    }

    private Map<String, String> validateArtifacts(JsonNode artifacts, String versionId, String status) {
        Map<String, String> hashes = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = artifacts.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if ("manifest_sha256".equals(field.getKey())) {
                continue;
            }
            if (!ARTIFACT_KEYS.contains(field.getKey())) {
                throw new PreprocessingProcessException("ARTIFACT_MANIFEST_MISMATCH");
            }
            JsonNode ref = field.getValue();
            if (!ref.isObject() || !ref.path("path").isTextual() || !ref.path("sha256").isTextual()) {
                throw new PreprocessingProcessException("ARTIFACT_MANIFEST_MISMATCH");
            }
            Path path = Path.of(ref.path("path").asText());
            if (containsSymlink(path) || !Files.isRegularFile(path) || !SHA256.matcher(ref.path("sha256").asText()).matches()
                    || !ref.path("sha256").asText().equals(sha256(path))) {
                throw new PreprocessingProcessException("ARTIFACT_MANIFEST_MISMATCH");
            }
            hashes.put(field.getKey(), ref.path("sha256").asText());
        }
        if (!hashes.containsKey("manifest") || !hashes.containsKey("version")
                || ("READY".equals(status) && !hashes.keySet().containsAll(List.of("chunks", "document_tree", "issues")))) {
            throw new PreprocessingProcessException("ARTIFACT_MANIFEST_MISMATCH");
        }
        JsonNode version = readArtifactJson(artifacts.path("version"));
        if (!versionId.equals(version.path("version_id").asText())) {
            throw new PreprocessingProcessException("VERSION_MANIFEST_MISMATCH");
        }
        return Map.copyOf(hashes);
    }

    private JsonNode readArtifactJson(JsonNode ref) {
        if (!ref.isObject() || !ref.path("path").isTextual()) {
            throw new PreprocessingProcessException("ARTIFACT_MANIFEST_MISMATCH");
        }
        try {
            Path path = Path.of(ref.path("path").asText());
            if (containsSymlink(path)) {
                throw new PreprocessingProcessException("ARTIFACT_MANIFEST_MISMATCH");
            }
            return objectMapper.readTree(Files.readString(path));
        } catch (IOException | RuntimeException exception) {
            throw new PreprocessingProcessException("ARTIFACT_MANIFEST_MISMATCH", exception);
        }
    }

    private static boolean containsSymlink(Path path) {
        for (Path current = path; current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current)) {
                return true;
            }
        }
        return false;
    }

    private static String sha256(Path path) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (Exception exception) {
            throw new PreprocessingProcessException("ARTIFACT_MANIFEST_MISMATCH", exception);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }

    private static String text(JsonNode node, String name) {
        String value = node.path(name).asText("");
        if (value.isBlank()) {
            throw new PreprocessingProcessException("ARTIFACT_MANIFEST_MISMATCH");
        }
        return value;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static String safeCode(String code) {
        return code.matches("[A-Z0-9_]{1,80}") ? code : "PREPROCESSING_FAILED";
    }
}
