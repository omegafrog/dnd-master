package com.dndmaster.aigamemaster.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Local Codex app-server JSON-RPC client. One app-server process is shared per local configuration. */
public final class CodexAppServerClient implements AutoCloseable {
    private static final ConcurrentHashMap<String, CodexAppServerClient> SHARED = new ConcurrentHashMap<>();
    private static final Logger LOGGER = LoggerFactory.getLogger(CodexAppServerClient.class);

    public static CodexAppServerClient shared(String executable, Path workDirectory, Duration timeout, ObjectMapper mapper) {
        String key = executable + "|" + workDirectory.toAbsolutePath().normalize();
        return SHARED.computeIfAbsent(key, ignored -> new CodexAppServerClient(executable, workDirectory, timeout, mapper));
    }

    private final String executable;
    private final Path workDirectory;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private Process process;
    private BufferedWriter input;
    private BufferedReader output;
    private long requestId;

    private CodexAppServerClient(String executable, Path workDirectory, Duration timeout, ObjectMapper mapper) {
        this.executable = require(executable, "Codex executable");
        this.workDirectory = workDirectory.toAbsolutePath().normalize();
        this.timeout = timeout;
        this.mapper = mapper;
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("Codex timeout must be positive");
    }

    public synchronized String complete(String operationId, String prompt, String requestedModel) {
        return complete(operationId, prompt, requestedModel, "unknown");
    }

    public synchronized String complete(String operationId, String prompt, String requestedModel, String reasoning) {
        return complete(operationId, prompt, requestedModel, reasoning, null);
    }
    public synchronized String complete(String operationId, String prompt, String requestedModel, String reasoning, JsonNode outputSchema) {
        if (operationId == null || operationId.isBlank() || prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("operation id and prompt required");
        }
        long startedAt = System.nanoTime();
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        String model = requestedModel == null || requestedModel.isBlank() ? "default" : requestedModel;
        LOGGER.info("ai_agent_call_started provider=codex operationId={} stage={} model={} reasoning={} promptChars={} estimatedPromptTokens={} turnId={} turnCompletedReceived={} timeout={}",
                safe(operationId), stage(operationId), safe(model), safe(reasoning), prompt.length(), AiCallObservability.estimatedTokens(prompt.length()), "unknown", false, false);
        String threadId = "unknown";
        boolean turnCompletedReceived = false;
        try {
            ensureStarted(deadlineNanos);
            ObjectNode threadParams = mapper.createObjectNode();
            threadParams.put("cwd", workDirectory.toString());
            threadParams.put("approvalPolicy", "never");
            threadParams.put("sandbox", "read-only");
            threadParams.put("ephemeral", true);
            putModel(threadParams, requestedModel);
            JsonNode thread = request("thread/start", threadParams, deadlineNanos).path("thread");
            threadId = thread.path("id").asText("");
            if (threadId.isBlank()) throw new IllegalStateException("Codex app-server did not return a thread id");

            ObjectNode turnParams = mapper.createObjectNode();
            turnParams.put("threadId", threadId);
            ArrayNode inputItems = turnParams.putArray("input");
            ObjectNode text = inputItems.addObject();
            text.put("type", "text");
            text.put("text", prompt);
            putModel(turnParams, requestedModel);
            turnParams.put("approvalPolicy", "never");
            turnParams.putObject("sandboxPolicy").put("type", "readOnly");
            if (outputSchema != null) turnParams.set("outputSchema", outputSchema);
            request("turn/start", turnParams, deadlineNanos);

            StringBuilder response = new StringBuilder();
            String lastMethod = "turn/start";
            while (true) {
                JsonNode message;
                try {
                    message = readMessageUntil(deadlineNanos);
                } catch (ProviderTimeoutException exception) {
                    CodexTurnTimeoutException timeoutFailure = exception instanceof CodexTurnTimeoutException structured
                            ? structured
                            : timeoutFailure(operationId, threadId, model, reasoning, startedAt, lastMethod, exception);
                    LOGGER.error("ai_agent_call_timeout provider=codex operationId={} stage={} model={} reasoning={} promptChars={} estimatedPromptTokens={} responseChars={} turnId={} turnCompletedReceived={} timeout=true lastMethod={} timeoutMs={}",
                            safe(operationId), stage(operationId), safe(model), safe(reasoning), prompt.length(), AiCallObservability.estimatedTokens(prompt.length()), response.length(), safe(threadId), turnCompletedReceived, safe(lastMethod), timeout.toMillis());
                    cancelTurnAsync(threadId, operationId);
                    throw timeoutFailure;
                }
                String method = message.path("method").asText("");
                if (!method.isBlank()) lastMethod = method;
                JsonNode params = message.path("params");
                if (!method.isBlank() && !"item/agentMessage/delta".equals(method)) {
                    LOGGER.info("ai_agent_event provider=codex operationId={} stage={} method={} turnId={}", safe(operationId), stage(operationId), safe(method), safe(threadId));
                }
                if ("item/agentMessage/delta".equals(method)) response.append(params.path("delta").asText(""));
                if ("item/completed".equals(method)) appendCompletedAgentMessage(response, params.path("item"));
                if ("turn/completed".equals(method)) {
                    turnCompletedReceived = true;
                    JsonNode turnError = params.path("turn").path("error");
                    if (!turnError.isMissingNode() && !turnError.isNull()) {
                        throw new CodexTurnFailedException(threadId, method, "Codex turn failed: " + compact(turnError));
                    }
                    break;
                }
                if ("turn/failed".equals(method) || "turn/aborted".equals(method)) {
                    throw new CodexTurnFailedException(threadId, method, "Codex turn terminated: " + compact(params));
                }
                if (message.has("error")) throw rpcError(message);
            }
            if (response.isEmpty()) throw new ProviderMalformedResponseException("Codex app-server response missing text");
            String result = response.toString().trim();
            LOGGER.info("ai_agent_call_completed provider=codex operationId={} stage={} durationMs={} promptChars={} estimatedPromptTokens={} responseChars={} model={} reasoning={} turnId={} turnCompletedReceived=true timeout=false",
                    safe(operationId), stage(operationId), elapsedMillis(startedAt), prompt.length(), AiCallObservability.estimatedTokens(prompt.length()), result.length(), safe(model), safe(reasoning), safe(threadId));
            return result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.warn("ai_agent_call_interrupted provider=codex operationId={} stage={} durationMs={} turnId={} turnCompletedReceived={} timeout=true", safe(operationId), stage(operationId), elapsedMillis(startedAt), safe(threadId), turnCompletedReceived);
            throw new ProviderTimeoutException(exception);
        } catch (IOException exception) {
            closeProcess();
            LOGGER.error("ai_agent_call_failed provider=codex operationId={} durationMs={} errorType={} message={}", operationId,
                    elapsedMillis(startedAt), exception.getClass().getSimpleName(), safeMessage(exception));
            throw new ProviderTimeoutException(exception);
        } catch (ProviderTimeoutException exception) {
            CodexTurnTimeoutException timeoutFailure = exception instanceof CodexTurnTimeoutException structured
                    ? structured
                    : timeoutFailure(operationId, threadId, model, reasoning, startedAt, "request", exception);
            cancelTurnAsync(threadId, operationId);
            LOGGER.error("ai_agent_call_timeout provider=codex operationId={} stage={} model={} reasoning={} promptChars={} estimatedPromptTokens={} responseChars={} turnId={} turnCompletedReceived={} timeout=true phase=app-server-request timeoutMs={}",
                    safe(operationId), stage(operationId), safe(model), safe(reasoning), prompt.length(), AiCallObservability.estimatedTokens(prompt.length()), 0, safe(threadId), turnCompletedReceived, timeout.toMillis());
            throw timeoutFailure;
        } catch (RuntimeException exception) {
            LOGGER.error("ai_agent_call_failed provider=codex operationId={} durationMs={} errorType={} message={}", operationId,
                    elapsedMillis(startedAt), exception.getClass().getSimpleName(), safeMessage(exception));
            throw exception;
        }
    }

    private static long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private CodexTurnTimeoutException timeoutFailure(String operationId, String turnId, String model,
            String reasoning, long startedAt, String lastEvent, Throwable cause) {
        return new CodexTurnTimeoutException(operationId, turnId, stage(operationId), model, reasoning,
                elapsedMillis(startedAt), timeout.toMillis(), lastEvent, cause);
    }

    /** Sends cancellation best-effort and never makes the caller wait for the app-server acknowledgement. */
    private void cancelTurnAsync(String turnId, String operationId) {
        if (turnId == null || turnId.isBlank() || "unknown".equals(turnId)) return;
        CompletableFuture.runAsync(() -> {
            try {
                synchronized (CodexAppServerClient.this) {
                    if (process == null || !process.isAlive() || input == null) return;
                    ObjectNode params = mapper.createObjectNode().put("turnId", turnId);
                    ObjectNode cancel = mapper.createObjectNode().put("method", "turn/cancel");
                    cancel.set("params", params);
                    send(cancel);
                    LOGGER.info("ai_agent_turn_cancel_sent provider=codex operationId={} turnId={}", safe(operationId), safe(turnId));
                }
            } catch (Exception cancellationFailure) {
                LOGGER.warn("ai_agent_turn_cancel_failed provider=codex operationId={} turnId={} error={}",
                        safe(operationId), safe(turnId), safeMessage(cancellationFailure));
            } finally {
                synchronized (CodexAppServerClient.this) { closeProcess(); }
            }
        });
    }

    static String stage(String operationId) {
        if (operationId == null) return "unknown";
        if (operationId.contains(":resolution-candidate-repair")) return "resolution-candidate-repair";
        if (operationId.contains(":resolution-candidates")) return "resolution-candidate-extraction";
        if (operationId.endsWith("-verification")) return "story-plan-verification";
        if (operationId.endsWith("-execution-projection")) return "story-plan-projection";
        if (operationId.endsWith("-projection-repair")) return "story-plan-projection-repair";
        return "story-plan-generation";
    }

    private static String safe(String value) { return AiCallObservability.safe(value); }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) return "<none>";
        String normalized = message.replaceAll("[\\r\\n]+", " ");
        return normalized.substring(0, Math.min(normalized.length(), 240));
    }

    private static String compact(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return "{}";
        String value = node.toString();
        return value.length() <= 2000 ? value : value.substring(0, 2000) + "…";
    }

    public synchronized boolean isAuthenticated() {
        try {
            long deadlineNanos = System.nanoTime() + timeout.toNanos();
            ensureStarted(deadlineNanos);
            JsonNode account = request("account/read", mapper.createObjectNode(), deadlineNanos).path("account");
            return !account.isMissingNode()
                    && !account.isNull()
                    && !account.path("type").asText("").isBlank();
        } catch (IOException exception) {
            closeProcess();
            throw new IllegalStateException("Codex app-server account check failed", exception);
        } catch (ProviderTimeoutException exception) {
            closeProcess();
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProviderTimeoutException(exception);
        }
    }

    public synchronized String startBrowserLogin() {
        try {
            long deadlineNanos = System.nanoTime() + timeout.toNanos();
            ensureStarted(deadlineNanos);
            ObjectNode params = mapper.createObjectNode().put("type", "chatgpt");
            String authUrl = request("account/login/start", params, deadlineNanos).path("authUrl").asText("");
            if (authUrl.isBlank()) throw new IllegalStateException("Codex app-server did not return an OAuth URL");
            return authUrl;
        } catch (IOException exception) {
            closeProcess();
            throw new IllegalStateException("Codex OAuth could not be started", exception);
        } catch (ProviderTimeoutException exception) {
            closeProcess();
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProviderTimeoutException(exception);
        }
    }

    private void ensureStarted(long deadlineNanos) throws IOException, InterruptedException {
        if (process != null && process.isAlive()) return;
        closeProcess();
        process = new ProcessBuilder(List.of(executable, "app-server", "--stdio"))
                .directory(workDirectory.toFile())
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        input = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        output = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        ObjectNode capabilities = mapper.createObjectNode();
        capabilities.putArray("optOutNotificationMethods").add("item/agentMessage/delta");
        ObjectNode initialize = mapper.createObjectNode();
        initialize.putObject("clientInfo").put("name", "dnd-master").put("title", "D&D Master").put("version", "0.1.0");
        initialize.set("capabilities", capabilities);
        request("initialize", initialize, deadlineNanos);
        send(mapper.createObjectNode().put("method", "initialized"));
    }

    private JsonNode request(String method, JsonNode params, long deadlineNanos) throws IOException, InterruptedException {
        long id = ++requestId;
        ObjectNode request = mapper.createObjectNode();
        request.put("id", id);
        request.put("method", method);
        request.set("params", params);
        send(request);
        while (true) {
            JsonNode message = readMessageUntil(deadlineNanos);
            if (message.path("id").asLong(Long.MIN_VALUE) == id) {
                if (message.has("error")) throw rpcError(message);
                return message.path("result");
            }
            handleServerRequest(message);
        }
    }

    private JsonNode readMessage() throws IOException {
        String line = output.readLine();
        if (line == null) throw new IOException("Codex app-server closed stdout");
        return mapper.readTree(line);
    }

    private JsonNode readMessageUntil(long deadlineNanos) throws IOException, InterruptedException {
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) throw new ProviderTimeoutException(new TimeoutException("Codex turn event deadline exceeded"));
        CompletableFuture<JsonNode> read = CompletableFuture.supplyAsync(() -> {
            try {
                return readMessage();
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        });
        try {
            return read.get(remainingNanos, TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            read.cancel(true);
            throw new ProviderTimeoutException(exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime && runtime.getCause() instanceof IOException io) throw io;
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Codex app-server reader failed", cause);
        }
    }

    private void send(JsonNode message) throws IOException {
        input.write(mapper.writeValueAsString(message));
        input.newLine();
        input.flush();
    }

    private void handleServerRequest(JsonNode message) throws IOException {
        if (!message.has("id") || !message.has("method")) return;
        ObjectNode response = mapper.createObjectNode();
        response.put("id", message.path("id").asLong());
        response.putObject("error").put("code", -32000).put("message", "D&D Master does not support interactive app-server requests");
        send(response);
    }

    private static void appendCompletedAgentMessage(StringBuilder response, JsonNode item) {
        if (!"agentMessage".equals(item.path("type").asText()) || !response.isEmpty()) return;
        String text = item.path("text").asText("");
        if (!text.isBlank()) response.append(text);
    }

    private static void putModel(ObjectNode params, String model) {
        if (model != null && !model.isBlank() && !"codex-cli".equalsIgnoreCase(model)) params.put("model", model);
    }

    private static IllegalStateException rpcError(JsonNode message) {
        return new IllegalStateException("Codex app-server error: " + message.path("error").path("message").asText("unknown error"));
    }

    private void closeProcess() {
        BufferedWriter currentInput = input;
        BufferedReader currentOutput = output;
        input = null;
        output = null;
        try { if (currentInput != null) currentInput.close(); } catch (IOException ignored) { }
        try { if (currentOutput != null) currentOutput.close(); } catch (IOException ignored) { }
        if (process != null) {
            process.destroy();
            try { process.waitFor(2, TimeUnit.SECONDS); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            if (process.isAlive()) process.destroyForcibly();
        }
        process = null;
    }

    @Override public synchronized void close() { closeProcess(); }

    private static String require(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " required");
        return value.trim();
    }
}
