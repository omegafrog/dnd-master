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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Local Codex app-server JSON-RPC client. One app-server process is shared per local configuration. */
public final class CodexAppServerClient implements AutoCloseable {
    private static final ConcurrentHashMap<String, CodexAppServerClient> SHARED = new ConcurrentHashMap<>();
    private static final Logger LOGGER = LoggerFactory.getLogger(CodexAppServerClient.class);
    static final int DEFAULT_MAX_DYNAMIC_TOOL_CALLS = 24;
    private static final int DEFAULT_MAX_DYNAMIC_TOOL_RESULT_CHARS = 120_000;

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

    public record DynamicTool(String name, String description, JsonNode inputSchema,
            Function<JsonNode, String> handler) {
        public DynamicTool {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("dynamic tool name required");
            if (description == null || description.isBlank()) throw new IllegalArgumentException("dynamic tool description required");
            if (inputSchema == null || !inputSchema.isObject()) throw new IllegalArgumentException("dynamic tool input schema must be an object");
            if (handler == null) throw new IllegalArgumentException("dynamic tool handler required");
        }
    }

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
        return complete(operationId, prompt, requestedModel, reasoning, null, List.of());
    }
    public synchronized String complete(String operationId, String prompt, String requestedModel, String reasoning, JsonNode outputSchema) {
        return complete(operationId, prompt, requestedModel, reasoning, outputSchema, List.of());
    }

    public synchronized String complete(String operationId, String prompt, String requestedModel, String reasoning,
            JsonNode outputSchema, List<DynamicTool> dynamicTools) {
        if (operationId == null || operationId.isBlank() || prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("operation id and prompt required");
        }
        dynamicTools = dynamicTools == null ? List.of() : List.copyOf(dynamicTools);
        DynamicToolCallTracker toolTracker = new DynamicToolCallTracker();
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
            if (!dynamicTools.isEmpty()) threadParams.set("dynamicTools", dynamicToolSpecs(dynamicTools));
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
                    LOGGER.error("ai_agent_call_timeout provider=codex operationId={} stage={} model={} reasoning={} promptChars={} estimatedPromptTokens={} responseChars={} turnId={} turnCompletedReceived={} timeout=true lastMethod={} timeoutMs={} toolSummary={}",
                            safe(operationId), stage(operationId), safe(model), safe(reasoning), prompt.length(), AiCallObservability.estimatedTokens(prompt.length()), response.length(), safe(threadId), turnCompletedReceived, safe(lastMethod), timeout.toMillis(), toolTracker.summary());
                    cancelTurnAsync(threadId, operationId);
                    throw timeoutFailure;
                }
                String method = message.path("method").asText("");
                if (!method.isBlank()) lastMethod = method;
                JsonNode params = message.path("params");
                if (message.has("id") && !message.has("result") && !message.has("error")) {
                    handleServerRequest(message, dynamicTools, operationId, toolTracker);
                    continue;
                }
                if (method.startsWith("item/commandExecution/")) toolTracker.recordCommandExecution(method);
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
            LOGGER.info("ai_agent_call_completed provider=codex operationId={} stage={} durationMs={} promptChars={} estimatedPromptTokens={} responseChars={} model={} reasoning={} turnId={} turnCompletedReceived=true timeout=false toolSummary={}",
                    safe(operationId), stage(operationId), elapsedMillis(startedAt), prompt.length(), AiCallObservability.estimatedTokens(prompt.length()), result.length(), safe(model), safe(reasoning), safe(threadId), toolTracker.summary());
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
            LOGGER.error("ai_agent_call_timeout provider=codex operationId={} stage={} model={} reasoning={} promptChars={} estimatedPromptTokens={} responseChars={} turnId={} turnCompletedReceived={} timeout=true phase=app-server-request timeoutMs={} toolSummary={}",
                    safe(operationId), stage(operationId), safe(model), safe(reasoning), prompt.length(), AiCallObservability.estimatedTokens(prompt.length()), 0, safe(threadId), turnCompletedReceived, timeout.toMillis(), toolTracker.summary());
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
        capabilities.put("experimentalApi", true);
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

    private void handleServerRequest(JsonNode message, List<DynamicTool> dynamicTools, String operationId,
            DynamicToolCallTracker toolTracker) throws IOException {
        if (!message.has("id") || !message.has("method")) return;
        if ("item/tool/call".equals(message.path("method").asText())) {
            handleDynamicToolCall(message, dynamicTools, operationId, toolTracker);
            return;
        }
        ObjectNode response = mapper.createObjectNode();
        response.set("id", message.get("id"));
        response.putObject("error").put("code", -32000).put("message", "D&D Master does not support interactive app-server requests");
        send(response);
    }

    private void handleDynamicToolCall(JsonNode message, List<DynamicTool> dynamicTools, String operationId,
            DynamicToolCallTracker toolTracker) throws IOException {
        JsonNode params = message.path("params");
        String toolName = params.path("tool").asText("");
        JsonNode arguments = params.path("arguments");
        DynamicToolCallTracker.Decision decision = toolTracker.beforeCall(toolName, arguments);
        LOGGER.info("ai_agent_dynamic_tool_requested provider=codex operationId={} tool={} callIndex={} queryChars={} queryHash={} allowed={} denialReason={} totalToolCalls={}",
                safe(operationId), safe(toolName), decision.callIndex(), decision.queryChars(), decision.queryHash(), decision.allowed(), safe(decision.denialReason()), toolTracker.totalCalls());
        DynamicTool tool = dynamicTools.stream().filter(candidate -> candidate.name().equals(toolName)).findFirst().orElse(null);
        boolean success = decision.allowed() && tool != null;
        String output;
        if (!decision.allowed()) {
            output = decision.denialReason();
        } else if (tool == null) {
            output = "Unknown dynamic tool: " + toolName;
        } else {
            try {
                output = tool.handler().apply(arguments);
                if (output == null || output.isBlank()) {
                    success = false;
                    output = "Dynamic tool returned no result";
                }
            } catch (RuntimeException failure) {
                success = false;
                output = "Dynamic tool failed: " + safeMessage(failure);
            }
        }
        if (success && !toolTracker.acceptResult(output.length())) {
            success = false;
            output = "Dynamic tool result budget exhausted; finalize using the evidence already collected.";
        }
        LOGGER.info("ai_agent_dynamic_tool_call provider=codex operationId={} tool={} success={} resultChars={}",
                safe(operationId), safe(toolName), success, output.length());
        ObjectNode response = mapper.createObjectNode();
        response.set("id", message.get("id"));
        ObjectNode result = response.putObject("result");
        result.put("success", success);
        result.putArray("contentItems").addObject().put("type", "inputText").put("text", output);
        send(response);
    }

    private ArrayNode dynamicToolSpecs(List<DynamicTool> dynamicTools) {
        ArrayNode specs = mapper.createArrayNode();
        for (DynamicTool tool : dynamicTools) {
            ObjectNode spec = specs.addObject();
            spec.put("type", "function");
            spec.put("name", tool.name());
            spec.put("description", tool.description());
            spec.set("inputSchema", tool.inputSchema().deepCopy());
        }
        return specs;
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

    private static final class DynamicToolCallTracker {
        private final Map<String, Integer> callsByTool = new LinkedHashMap<>();
        private final Set<String> queryKeys = new LinkedHashSet<>();
        private int totalCalls;
        private int totalResultChars;
        private int duplicateQueries;
        private int commandExecutionEvents;

        private Decision beforeCall(String toolName, JsonNode arguments) {
            int callIndex = ++totalCalls;
            String query = arguments == null ? "" : arguments.path("query").asText("").trim();
            String normalizedQuery = query.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
            String queryKey = toolName + "|" + normalizedQuery;
            boolean duplicate = !normalizedQuery.isBlank() && !queryKeys.add(queryKey);
            if (duplicate) duplicateQueries++;
            callsByTool.merge(toolName, 1, Integer::sum);
            if (callIndex > DEFAULT_MAX_DYNAMIC_TOOL_CALLS) {
                return new Decision(false, callIndex, query.length(), fingerprint(query),
                        "Dynamic tool call budget exhausted; finalize using the evidence already collected.");
            }
            if (duplicate) {
                return new Decision(false, callIndex, query.length(), fingerprint(query),
                        "This dynamic tool query was already executed; finalize using the existing result.");
            }
            return new Decision(true, callIndex, query.length(), fingerprint(query), "");
        }

        private boolean acceptResult(int resultChars) {
            if (totalResultChars + resultChars > DEFAULT_MAX_DYNAMIC_TOOL_RESULT_CHARS) return false;
            totalResultChars += resultChars;
            return true;
        }

        private void recordCommandExecution(String method) {
            commandExecutionEvents++;
        }

        private int totalCalls() { return totalCalls; }

        private String summary() {
            return "{callsByTool=" + callsByTool + ", totalToolCalls=" + totalCalls
                    + ", totalResultChars=" + totalResultChars + ", duplicateQueries=" + duplicateQueries
                    + ", commandExecutionEvents=" + commandExecutionEvents + "}";
        }

        private static String fingerprint(String query) {
            return query.isBlank() ? "<none>" : Integer.toHexString(query.hashCode());
        }

        private record Decision(boolean allowed, int callIndex, int queryChars, String queryHash, String denialReason) { }
    }
}
