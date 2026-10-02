package com.dndmaster.aigamemaster.localcodex;

import com.dndmaster.aigamemaster.application.ai.AiExecutionFailure;
import com.dndmaster.aigamemaster.application.ai.AiExecutionPort;
import com.dndmaster.aigamemaster.application.ai.AiExecutionRequest;
import com.dndmaster.aigamemaster.application.ai.AiExecutionResult;
import com.dndmaster.aigamemaster.application.ai.AiExecutionSuccess;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Executes one non-interactive Codex CLI process and exposes a run-scoped MCP search server. */
public final class LocalCodexAiExecutionPort implements AiExecutionPort, AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalCodexAiExecutionPort.class);
    private static final int MAX_DIAGNOSTIC_BYTES = 8 * 1024;
    private final String executable;
    private final Path workDirectory;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private volatile RagSearchHandler ragSearchHandler = (requestId, query) -> {
        throw new IllegalStateException("RAG search is unavailable for this execution");
    };
    private volatile Process process;

    public LocalCodexAiExecutionPort(String executable, Path workDirectory, Duration timeout, ObjectMapper mapper) {
        this.executable = required(executable, "Codex executable");
        this.workDirectory = workDirectory.toAbsolutePath().normalize();
        this.timeout = java.util.Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("timeout must be positive");
        this.mapper = mapper;
    }

    public void setRagSearchHandler(RagSearchHandler handler) {
        this.ragSearchHandler = java.util.Objects.requireNonNull(handler);
    }

    @Override
    public synchronized AiExecutionResult execute(AiExecutionRequest request) {
        Path scratch = null;
        McpRelayServer relay = null;
        try {
            Files.createDirectories(workDirectory);
            scratch = Files.createTempDirectory("dnd-codex-exec-");
            Path output = scratch.resolve("final.txt");
            Path schema = scratch.resolve("schema.json");
            boolean hasOutputSchema = request.outputSchema() != null && !request.outputSchema().isNull();
            if (hasOutputSchema) Files.writeString(schema, mapper.writeValueAsString(request.outputSchema()));
            List<String> command = new ArrayList<>(List.of(executable, "exec", "--ephemeral", "--ignore-user-config",
                    "--json", "--output-last-message", output.toString(), "-m", request.model()));
            if (hasOutputSchema) command.addAll(List.of("--output-schema", schema.toString()));
            command.addAll(List.of("-c", "model_reasoning_effort=" + toml(request.reasoning())));
            if (request.ragSearchContext() != null && !request.ragSearchContext().isNull()) {
                relay = new McpRelayServer(request.requestId(), ragSearchHandler, mapper);
                relay.start();
                String classPath = System.getProperty("java.class.path");
                command.addAll(List.of("-c", "mcp_servers.rag_search.command=" + toml(javaExecutable()),
                        "-c", "mcp_servers.rag_search.enabled=true",
                        "-c", "mcp_servers.rag_search.required=true",
                        "-c", "mcp_servers.rag_search.default_tools_approval_mode=\"auto\"",
                        "-c", "mcp_servers.rag_search.tools.search_rules.approval_mode=\"auto\"",
                        "-c", "mcp_servers.rag_search.enabled_tools=[\"search_rules\"]",
                        "-c", "mcp_servers.rag_search.startup_timeout_sec=20",
                        "-c", "mcp_servers.rag_search.tool_timeout_sec=150",
                        "-c", "mcp_servers.rag_search.args=[" + toml("-cp") + "," + toml(classPath) + ","
                                + toml(McpStdioServer.class.getName()) + "," + toml("127.0.0.1") + ","
                                + toml(Integer.toString(relay.port())) + "," + toml(relay.token()) + "]"));
            }
            command.addAll(List.of("-C", workDirectory.toString(), "-"));
            ProcessBuilder builder = new ProcessBuilder(command).directory(workDirectory.toFile());
            if (!request.imageDataUri().isBlank()) {
                String mime = request.imageDataUri().substring("data:".length(), request.imageDataUri().indexOf(';'));
                String extension = mime.toLowerCase().contains("png") ? ".png" : mime.toLowerCase().contains("webp") ? ".webp" : ".jpg";
                Path image = scratch.resolve("input-image" + extension);
                String encoded = request.imageDataUri().substring(request.imageDataUri().indexOf(',') + 1);
                Files.write(image, Base64.getDecoder().decode(encoded));
                int promptIndex = command.size() - 1;
                command.add(promptIndex, "--image");
                command.add(promptIndex + 1, image.toString());
                builder = new ProcessBuilder(command).directory(workDirectory.toFile());
            }
            Process running = builder.start();
            process = running;
            BoundedCapture stderr = new BoundedCapture(running.getErrorStream(), MAX_DIAGNOSTIC_BYTES);
            BoundedCapture stdout = new BoundedCapture(running.getInputStream(), MAX_DIAGNOSTIC_BYTES);
            Thread stderrThread = new Thread(stderr, "codex-exec-stderr");
            Thread stdoutThread = new Thread(stdout, "codex-exec-stdout");
            stderrThread.setDaemon(true); stdoutThread.setDaemon(true); stderrThread.start(); stdoutThread.start();
            try {
                running.getOutputStream().write(request.completedPrompt().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (IOException inputFailure) {
                if (running.isAlive()) throw inputFailure;
            } finally {
                try { running.getOutputStream().close(); } catch (IOException ignored) { }
            }
            if (!running.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                stopProcessTree(running);
                return new AiExecutionFailure(AiExecutionFailure.Reason.TIMEOUT, "codex exec timed out");
            }
            stderrThread.join(1_000);
            stdoutThread.join(1_000);
            if (running.exitValue() != 0 || !Files.exists(output)) {
                String diagnostic = firstNonBlank(stderr.text(), jsonFailureMessage(stdout.text()));
                LOGGER.warn("codex exec failed requestId={} exitCode={} outputFilePresent={} diagnostic={}",
                        request.requestId(), running.exitValue(), Files.exists(output), diagnostic);
                return new AiExecutionFailure(AiExecutionFailure.Reason.LOCAL_EXECUTION_FAILED,
                        "codex exec exited with status " + running.exitValue()
                                + (diagnostic.isBlank() ? "" : ": " + diagnostic));
            }
            return new AiExecutionSuccess(Files.readString(output), com.dndmaster.aigamemaster.application.ai.AiExecutionUsage.unknown());
        } catch (Exception failure) {
            return new AiExecutionFailure(AiExecutionFailure.Reason.LOCAL_EXECUTION_FAILED,
                    failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
        } finally {
            process = null;
            if (relay != null) relay.close();
            if (scratch != null) deleteTree(scratch);
        }
    }

    @Override public synchronized void close() { if (process != null) stopProcessTree(process); }

    private static String javaExecutable() {
        return Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java").toString();
    }
    private static boolean isWindows() { return System.getProperty("os.name", "").toLowerCase().contains("win"); }
    private static String toml(String value) { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
    private static String required(String value, String name) { if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required"); return value.trim(); }
    private static void drain(java.io.InputStream stream) { try (stream) { stream.transferTo(java.io.OutputStream.nullOutputStream()); } catch (IOException ignored) { } }
    private String jsonFailureMessage(String capturedOutput) {
        for (String line : capturedOutput.split("\\R")) {
            try {
                JsonNode event = mapper.readTree(line);
                String type = event.path("type").asText();
                if ("turn.failed".equals(type) || "error".equals(type)) {
                    JsonNode error = event.path("error");
                    String message = error.path("message").asText();
                    if (message.isBlank() && error.isTextual()) message = error.asText();
                    if (message.isBlank()) message = event.path("message").asText();
                    if (!message.isBlank()) return message;
                }
            } catch (IOException ignored) { }
        }
        return "";
    }
    private static String firstNonBlank(String first, String second) { return first == null || first.isBlank() ? second : first; }
    private static final class BoundedCapture implements Runnable {
        private final java.io.InputStream stream;
        private final int maxBytes;
        private byte[] tail = new byte[0];
        private BoundedCapture(java.io.InputStream stream, int maxBytes) { this.stream = stream; this.maxBytes = maxBytes; }
        @Override public void run() {
            try (stream) {
                byte[] chunk = new byte[1024];
                int count;
                while ((count = stream.read(chunk)) >= 0) {
                    synchronized (this) {
                        int retained = Math.min(maxBytes, tail.length + count);
                        byte[] next = new byte[retained];
                        int oldStart = Math.min(tail.length, Math.max(0, tail.length + count - maxBytes));
                        int fromOld = Math.max(0, Math.min(tail.length - oldStart, retained));
                        if (fromOld > 0) System.arraycopy(tail, oldStart, next, 0, fromOld);
                        int fromChunk = retained - fromOld;
                        int chunkStart = Math.max(0, count - fromChunk);
                        if (fromChunk > 0) System.arraycopy(chunk, chunkStart, next, fromOld, fromChunk);
                        tail = next;
                    }
                }
            } catch (IOException ignored) { }
        }
        private synchronized String text() {
            return new String(tail, java.nio.charset.StandardCharsets.UTF_8)
                    .replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "?").trim();
        }
    }
    private static void stopProcessTree(Process parent) {
        parent.toHandle().descendants().forEach(handle -> { try { handle.destroyForcibly(); } catch (RuntimeException ignored) { } });
        parent.destroyForcibly();
    }
    private static void deleteTree(Path root) {
        try (var paths = Files.walk(root)) { paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException ignored) { } }); }
        catch (IOException ignored) { }
    }

    private static final class McpRelayServer implements AutoCloseable {
        private final String requestId; private final RagSearchHandler handler; private final ObjectMapper mapper;
        private final ServerSocket server; private final String token = UUID.randomUUID().toString(); private final AtomicBoolean closed = new AtomicBoolean();
        private McpRelayServer(String requestId, RagSearchHandler handler, ObjectMapper mapper) throws IOException {
            this.requestId = requestId; this.handler = handler; this.mapper = mapper;
            this.server = new ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"));
        }
        int port() { return server.getLocalPort(); } String token() { return token; }
        void start() { Thread thread = new Thread(() -> {
            try (var socket = server.accept(); var in = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                 var out = new java.io.BufferedWriter(new java.io.OutputStreamWriter(socket.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String greeting = in.readLine(); if (!token.equals(greeting)) return;
                String line; while (!closed.get() && (line = in.readLine()) != null) {
                    JsonNode call = mapper.readTree(line); JsonNode result = handler.search(requestId, call.path("query").asText());
                    out.write(mapper.writeValueAsString(result)); out.newLine(); out.flush();
                }
            } catch (Exception ignored) { }
        }, "local-mcp-relay"); thread.setDaemon(true); thread.start(); }
        @Override public void close() { closed.set(true); try { server.close(); } catch (IOException ignored) { } }
    }
    @FunctionalInterface public interface RagSearchHandler { JsonNode search(String requestId, String query) throws Exception; }
}
