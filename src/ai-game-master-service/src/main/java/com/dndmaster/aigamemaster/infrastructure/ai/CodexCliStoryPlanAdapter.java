package com.dndmaster.aigamemaster.infrastructure.ai;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Codex OAuth adapter. Authentication is supplied by the local Codex CLI session. */
public final class CodexCliStoryPlanAdapter implements StoryPlanCompletionPort {
    private final String executable;
    private final String model;
    private final Path workDirectory;
    private final Duration timeout;

    public CodexCliStoryPlanAdapter(String executable, String model, Path workDirectory, Duration timeout) {
        this.executable = require(executable, "Codex executable");
        this.model = require(model, "Codex model");
        this.workDirectory = Objects.requireNonNull(workDirectory, "Codex work directory");
        this.timeout = Objects.requireNonNull(timeout, "Codex timeout");
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("Codex timeout must be positive");
    }

    @Override
    public String complete(String operationId, String prompt) {
        if (operationId == null || operationId.isBlank() || prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("operation id and prompt required");
        }
        Path output = null;
        try {
            output = Files.createTempFile("dnd-codex-story-plan-", ".json");
            List<String> command = new ArrayList<>(List.of(executable, "exec", "--ephemeral",
                    "--skip-git-repo-check", "--ignore-user-config", "--ignore-rules", "--sandbox", "read-only",
                    "--model", model, "--cd", workDirectory.toString(), "--output-last-message", output.toString(),
                    "-c", "model_reasoning_effort=\"none\"", "-"));
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            try (OutputStream stdin = process.getOutputStream()) { stdin.write(prompt.getBytes(StandardCharsets.UTF_8)); }
            java.io.ByteArrayOutputStream logs = new java.io.ByteArrayOutputStream();
            Thread drainer = Thread.ofVirtual().start(() -> { try { process.getInputStream().transferTo(logs); } catch (IOException ignored) { } });
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new ProviderTimeoutException(new java.util.concurrent.TimeoutException("Codex CLI timed out"));
            }
            drainer.join();
            if (process.exitValue() != 0) {
                String log = logs.toString(StandardCharsets.UTF_8);
                throw new IllegalStateException("Codex CLI exited with status " + process.exitValue() + ": "
                        + log.substring(Math.max(0, log.length() - 800)).replaceAll("\\s+", " "));
            }
            String response = Files.readString(output).trim();
            if (response.isBlank()) throw new ProviderMalformedResponseException("Codex CLI response missing text");
            return response;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProviderTimeoutException(exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Codex CLI invocation failed", exception);
        } finally {
            if (output != null) try { Files.deleteIfExists(output); } catch (IOException ignored) { }
        }
    }

    private static String require(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " required");
        return value.trim();
    }
}
