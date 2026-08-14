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

/** Invokes the locally authenticated Codex CLI without putting source excerpts in process arguments. */
public final class CodexCliCharacterTagProvider {
    private final CommandRunner runner;
    private final CodexAppServerClient appServer;
    private final String model;
    private final List<String> command;
    private final Duration timeout;

    public CodexCliCharacterTagProvider(String executable, String model, Path workDirectory, Duration timeout) {
        this.runner = null;
        this.appServer = CodexAppServerClient.shared(executable, workDirectory, timeout, new com.fasterxml.jackson.databind.ObjectMapper());
        this.model = model;
        this.command = List.of();
        this.timeout = timeout;
    }

    CodexCliCharacterTagProvider(CommandRunner runner, String executable, String model, Path workDirectory, Duration timeout) {
        this.runner = Objects.requireNonNull(runner);
        this.appServer = null;
        this.model = model;
        if (executable == null || executable.isBlank() || model == null || model.isBlank()) throw new IllegalArgumentException("Codex executable and model required");
        this.timeout = Objects.requireNonNull(timeout);
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("Codex timeout must be positive");
        this.command = List.of(executable, "exec", "--ephemeral", "--skip-git-repo-check", "--ignore-user-config",
                "--ignore-rules", "--sandbox", "read-only", "--model", model, "--cd", Objects.requireNonNull(workDirectory).toString(), "-");
    }

    public String complete(String operationId, String prompt) {
        if (operationId == null || operationId.isBlank() || prompt == null || prompt.isBlank()) throw new IllegalArgumentException("operation id and prompt required");
        return appServer == null ? runner.run(command, prompt, timeout) : appServer.complete(operationId, prompt, model);
    }

    @FunctionalInterface
    interface CommandRunner { String run(List<String> command, String prompt, Duration timeout); }

    private static final class ProcessCommandRunner implements CommandRunner {
        @Override public String run(List<String> baseCommand, String prompt, Duration timeout) {
            Path output = null;
            try {
                output = Files.createTempFile("dnd-codex-character-tags-", ".json");
                List<String> command = new ArrayList<>(baseCommand);
                command.add("--output-last-message");
                command.add(output.toString());
                Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
                try (OutputStream stdin = process.getOutputStream()) { stdin.write(prompt.getBytes(StandardCharsets.UTF_8)); }
                java.io.ByteArrayOutputStream processOutput = new java.io.ByteArrayOutputStream();
                Thread outputDrainer = Thread.ofVirtual().start(() -> {
                    try { process.getInputStream().transferTo(processOutput); }
                    catch (IOException ignored) { }
                });
                if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                    throw new ProviderTimeoutException(new java.util.concurrent.TimeoutException("Codex CLI timed out"));
                }
                outputDrainer.join();
                if (process.exitValue() != 0) {
                    String log = processOutput.toString(StandardCharsets.UTF_8);
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
    }
}
