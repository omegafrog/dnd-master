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
import java.util.concurrent.TimeUnit;
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
        if (operationId == null || operationId.isBlank() || prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("operation id and prompt required");
        }
        long startedAt = System.nanoTime();
        LOGGER.info("ai_agent_call_started provider=codex operationId={} model={} promptLength={}", operationId,
                requestedModel == null || requestedModel.isBlank() ? "default" : requestedModel, prompt.length());
        try {
            ensureStarted();
            ObjectNode threadParams = mapper.createObjectNode();
            threadParams.put("cwd", workDirectory.toString());
            threadParams.put("approvalPolicy", "never");
            threadParams.put("sandbox", "read-only");
            threadParams.put("ephemeral", true);
            putModel(threadParams, requestedModel);
            JsonNode thread = request("thread/start", threadParams).path("thread");
            String threadId = thread.path("id").asText("");
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
            request("turn/start", turnParams);

            StringBuilder response = new StringBuilder();
            while (true) {
                JsonNode message = readMessage();
                String method = message.path("method").asText("");
                JsonNode params = message.path("params");
                if (!method.isBlank() && !"item/agentMessage/delta".equals(method)) {
                    LOGGER.info("ai_agent_event provider=codex operationId={} method={} params={}", operationId, method, compact(params));
                }
                if ("item/agentMessage/delta".equals(method)) response.append(params.path("delta").asText(""));
                if ("item/completed".equals(method)) appendCompletedAgentMessage(response, params.path("item"));
                if ("turn/completed".equals(method)) break;
                if ("turn/failed".equals(method) || "turn/aborted".equals(method)) {
                    throw new IllegalStateException("Codex turn terminated: " + compact(params));
                }
                if (message.has("error")) throw rpcError(message);
            }
            if (response.isEmpty()) throw new ProviderMalformedResponseException("Codex app-server response missing text");
            String result = response.toString().trim();
            LOGGER.info("ai_agent_call_completed provider=codex operationId={} durationMs={} responseLength={}", operationId,
                    elapsedMillis(startedAt), result.length());
            return result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.warn("ai_agent_call_interrupted provider=codex operationId={} durationMs={}", operationId, elapsedMillis(startedAt));
            throw new ProviderTimeoutException(exception);
        } catch (IOException exception) {
            closeProcess();
            LOGGER.error("ai_agent_call_failed provider=codex operationId={} durationMs={} errorType={} message={}", operationId,
                    elapsedMillis(startedAt), exception.getClass().getSimpleName(), safeMessage(exception));
            throw new IllegalStateException("Codex app-server invocation failed", exception);
        } catch (RuntimeException exception) {
            LOGGER.error("ai_agent_call_failed provider=codex operationId={} durationMs={} errorType={} message={}", operationId,
                    elapsedMillis(startedAt), exception.getClass().getSimpleName(), safeMessage(exception));
            throw exception;
        }
    }

    private static long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

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
            ensureStarted();
            JsonNode account = request("account/read", mapper.createObjectNode()).path("account");
            return !account.isMissingNode()
                    && !account.isNull()
                    && !account.path("type").asText("").isBlank();
        } catch (IOException exception) {
            closeProcess();
            throw new IllegalStateException("Codex app-server account check failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProviderTimeoutException(exception);
        }
    }

    public synchronized String startBrowserLogin() {
        try {
            ensureStarted();
            ObjectNode params = mapper.createObjectNode().put("type", "chatgpt");
            String authUrl = request("account/login/start", params).path("authUrl").asText("");
            if (authUrl.isBlank()) throw new IllegalStateException("Codex app-server did not return an OAuth URL");
            return authUrl;
        } catch (IOException exception) {
            closeProcess();
            throw new IllegalStateException("Codex OAuth could not be started", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProviderTimeoutException(exception);
        }
    }

    private void ensureStarted() throws IOException, InterruptedException {
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
        request("initialize", initialize);
        send(mapper.createObjectNode().put("method", "initialized"));
    }

    private JsonNode request(String method, JsonNode params) throws IOException, InterruptedException {
        long id = ++requestId;
        ObjectNode request = mapper.createObjectNode();
        request.put("id", id);
        request.put("method", method);
        request.set("params", params);
        send(request);
        while (true) {
            JsonNode message = readMessage();
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
        if (process != null) {
            process.destroy();
            try { process.waitFor(2, TimeUnit.SECONDS); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            if (process.isAlive()) process.destroyForcibly();
        }
        process = null;
        input = null;
        output = null;
    }

    @Override public synchronized void close() { closeProcess(); }

    private static String require(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " required");
        return value.trim();
    }
}
