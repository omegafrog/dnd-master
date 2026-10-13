package com.dndmaster.aigamemaster.localcodex;

import com.dndmaster.aigamemaster.application.ai.AiExecutionFailure;
import com.dndmaster.aigamemaster.application.ai.AiExecutionPort;
import com.dndmaster.aigamemaster.application.ai.AiExecutionRequest;
import com.dndmaster.aigamemaster.application.ai.AiExecutionResult;
import com.dndmaster.aigamemaster.application.ai.AiExecutionSuccess;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Persistent user-PC client that runs each relay request through the local Codex port. */
public final class CodexWebSocketAgent implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(CodexWebSocketAgent.class);

    private final HttpClient httpClient;
    private final URI relayEndpoint;
    private final String accessToken;
    private final String connectionId;
    private final AiExecutionPort executionPort;
    private final ObjectMapper objectMapper;
    private final Executor executionExecutor;
    private final CodexConnectionService connectionService;
    private final AtomicReference<WebSocket> socket = new AtomicReference<>();
    private final AtomicReference<CompletableFuture<Void>> executionTail =
            new AtomicReference<>(CompletableFuture.completedFuture(null));
    private final CompletableFuture<Void> closed = new CompletableFuture<>();
    private final java.util.Map<String, CompletableFuture<JsonNode>> toolCalls = new ConcurrentHashMap<>();

    public CodexWebSocketAgent(
            URI relayEndpoint,
            String accessToken,
            AiExecutionPort executionPort,
            ObjectMapper objectMapper) {
        this(relayEndpoint, accessToken, "", executionPort, objectMapper, ForkJoinPool.commonPool());
    }

    public CodexWebSocketAgent(
            URI relayEndpoint,
            String accessToken,
            AiExecutionPort executionPort,
            ObjectMapper objectMapper,
            Executor executionExecutor) {
        this(relayEndpoint, accessToken, "", executionPort, objectMapper, executionExecutor);
    }

    public CodexWebSocketAgent(
            URI relayEndpoint,
            String accessToken,
            String connectionId,
            AiExecutionPort executionPort,
            ObjectMapper objectMapper) {
        this(relayEndpoint, accessToken, connectionId, executionPort, objectMapper, ForkJoinPool.commonPool());
    }

    public CodexWebSocketAgent(
            URI relayEndpoint,
            String accessToken,
            String connectionId,
            AiExecutionPort executionPort,
            ObjectMapper objectMapper,
            Executor executionExecutor) {
        this(relayEndpoint, accessToken, connectionId, executionPort, objectMapper, executionExecutor, null);
    }

    public CodexWebSocketAgent(
            URI relayEndpoint,
            String accessToken,
            String connectionId,
            AiExecutionPort executionPort,
            ObjectMapper objectMapper,
            Executor executionExecutor,
            CodexConnectionService connectionService) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.relayEndpoint = requireWebSocketUri(relayEndpoint);
        this.accessToken = required(accessToken, "access token");
        this.connectionId = optionalConnectionId(connectionId);
        this.executionPort = Objects.requireNonNull(executionPort, "execution port must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.executionExecutor = Objects.requireNonNull(executionExecutor, "execution executor must not be null");
        this.connectionService = connectionService;
        if (executionPort instanceof LocalCodexAiExecutionPort localPort) {
            localPort.setRagSearchHandler(this::requestRagSearch);
        } else if (executionPort instanceof InstallationGuardedAiExecutionPort guardedPort) {
            guardedPort.setRagSearchHandler(this::requestRagSearch);
        }
    }

    /** Opens the persistent connection. The returned stage completes after the handshake. */
    public CompletionStage<WebSocket> connect() {
        if (socket.get() != null) {
            throw new IllegalStateException("WebSocket agent is already connected");
        }
        WebSocket.Builder builder = httpClient.newWebSocketBuilder()
                .header("Authorization", "Bearer " + accessToken);
        if (!connectionId.isEmpty()) {
            builder.header("X-Agent-Connection-Id", connectionId);
        }
        return builder
                .buildAsync(relayEndpoint, new Listener())
                .thenApply(webSocket -> {
                    if (socket.get() != webSocket) {
                        webSocket.abort();
                        throw new IllegalStateException("WebSocket agent connected more than once");
                    }
                    return webSocket;
                });
    }

    /** Completes when the server closes the connection or the agent is closed. */
    public CompletionStage<Void> completion() {
        return closed;
    }

    @Override
    public void close() {
        WebSocket current = socket.getAndSet(null);
        if (current == null) {
            closed.complete(null);
            return;
        }
        current.sendClose(WebSocket.NORMAL_CLOSURE, "agent closed")
                .whenComplete((ignored, failure) -> closed.complete(null));
    }

    private CompletableFuture<Void> executeAndReply(String message) {
        var stage = new AtomicReference<>("decode");
        RequestContext receivedContext = requestContext(message);
        var context = new AtomicReference<>(receivedContext);
        long started = System.nanoTime();
        devLog("request_received", receivedContext, "received", message.length(), started);
        return CompletableFuture.supplyAsync(() -> parseMessage(message), executionExecutor)
                .thenApply(decoded -> {
                    RequestContext decodedContext = requestContext(decoded);
                    context.set(decodedContext);
                    devLog("request_decoded", decodedContext, "decoded", null, started);
                    stage.set("dispatch");
                    devLog(decoded instanceof AgentExecutionRequest ? "codex_execution_started" : "control_request_started",
                            decodedContext, "started", null, started);
                    return dispatch(decoded);
                })
                .thenCompose(response -> {
                    stage.set("send_response");
                    RequestContext responseContext = context.get();
                    devLog("response_ready", responseContext, responseStatus(response), responseSize(response), started);
                    return sendResponse(response).whenComplete((ignored, failure) ->
                            devLog(failure == null ? "response_sent" : "response_send_failed", responseContext,
                                    failure == null ? "sent" : failure.getClass().getSimpleName(), null, started));
                })
                .exceptionally(failure -> {
                    Throwable cause = unwrap(failure);
                    RequestContext failedContext = context.get();
                    if ("unknown".equals(failedContext.requestId())) failedContext = requestContext(message);
                    devLog("request_failed", failedContext, stage.get() + ":" + cause.getClass().getSimpleName(), null, started);
                    LOGGER.warn("user-PC agent request failed requestId={} operationId={} stage={} causes={}",
                            failedContext.requestId(), failedContext.operationId(), stage.get(), causeSummary(cause));
                    return null;
                });
    }

    private static boolean diagnosticsEnabled() {
        return "true".equalsIgnoreCase(System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED",
                System.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED")));
    }

    private static String responseStatus(Object response) {
        if (response instanceof AgentExecutionResponse execution)
            return execution.failureType() == null ? "success" : execution.failureType();
        if (response instanceof AgentConnectionControlResponse control) return control.status();
        return response.getClass().getSimpleName();
    }

    private static int responseSize(Object response) {
        if (response instanceof AgentExecutionResponse execution)
            return execution.content() == null ? 0 : execution.content().length();
        return 0;
    }

    private static void devLog(String stage, RequestContext context, String outcome, Integer contentLength, long started) {
        if (!diagnosticsEnabled()) return;
        LOGGER.info("dev_user_pc_ai_execution requestId={} operationId={} stage={} outcome={} contentLength={} elapsedMs={}",
                context.requestId(), context.operationId(), stage, outcome,
                contentLength == null ? "none" : contentLength,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
    }

    private RequestContext requestContext(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            return new RequestContext(root.path("requestId").asText("unknown"),
                    root.path("operationId").asText("unknown"));
        } catch (JsonProcessingException ignored) {
            return new RequestContext("unknown", "unknown");
        }
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof java.util.concurrent.CompletionException
                || cause instanceof java.util.concurrent.ExecutionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static String causeSummary(Throwable failure) {
        StringBuilder result = new StringBuilder();
        Throwable cause = failure;
        for (int depth = 0; cause != null && depth < 5; depth++, cause = cause.getCause()) {
            if (result.length() > 0) result.append(" <- ");
            result.append(cause.getClass().getSimpleName());
            if (cause.getMessage() != null && !cause.getMessage().isBlank()) {
                String message = cause.getMessage()
                        .replaceAll("(?i)(authorization|token|secret|password|api[_-]?key)\\s*[:=]\\s*[^\\s,;]+", "$1=[redacted]")
                        .replaceAll("[\\r\\n\\t]+", " ");
                result.append(": ").append(message, 0, Math.min(message.length(), 240));
            }
        }
        return result.toString();
    }

    private record RequestContext(String requestId, String operationId) {}

    private Object parseMessage(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            if ("CONNECTION_CONTROL".equals(root.path("messageType").asText())) {
                return objectMapper.treeToValue(root, AgentConnectionControlCommand.class);
            }
            return objectMapper.treeToValue(root, AgentExecutionRequest.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("relay request could not be decoded", exception);
        }
    }

    private Object dispatch(Object message) {
        if (message instanceof AgentConnectionControlCommand command) return control(command);
        return execute((AgentExecutionRequest) message);
    }

    private static RequestContext requestContext(Object value) {
        if (value instanceof AgentExecutionRequest request) return new RequestContext(request.requestId(), request.operationId());
        if (value instanceof AgentExecutionResponse response) return new RequestContext(response.requestId(), "unknown");
        if (value instanceof AgentConnectionControlCommand command) return new RequestContext(command.requestId(), command.operationId());
        if (value instanceof AgentConnectionControlResponse response) return new RequestContext(response.requestId(), response.operationId());
        return new RequestContext("unknown", "unknown");
    }

    private AgentConnectionControlResponse control(AgentConnectionControlCommand command) {
        if (connectionService == null) {
            return new AgentConnectionControlResponse("CONNECTION_CONTROL_RESULT", command.requestId(),
                    "UNAVAILABLE", false, command.operationId(), null, false, null,
                    "현재 설치의 연결 기능을 사용할 수 없습니다.");
        }
        return switch (command.action()) {
            case "STATUS" -> AgentConnectionControlResponse.from(command.requestId(), connectionService.getStatus());
            case "START" -> AgentConnectionControlResponse.from(command.requestId(), connectionService.start(
                    command.operationId(), ConnectionOperationType.valueOf(command.operationType())));
            case "POLL" -> AgentConnectionControlResponse.from(command.requestId(), connectionService.getOperation(command.operationId()));
            case "DISCONNECT" -> AgentConnectionControlResponse.from(command.requestId(), connectionService.disconnect());
            default -> new AgentConnectionControlResponse("CONNECTION_CONTROL_RESULT", command.requestId(),
                    "FAILED", true, command.operationId(), null, false, null,
                    "지원하지 않는 연결 요청입니다.");
        };
    }

    private AgentExecutionRequest parseRequest(String message) {
        try {
            return objectMapper.readValue(message, AgentExecutionRequest.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("relay request could not be decoded", exception);
        }
    }

    AgentExecutionResponse execute(AgentExecutionRequest request) {
        long started = System.nanoTime();
        AiExecutionResult result = executionPort.execute(new AiExecutionRequest(
                request.soloPlayerId(),
                request.requestId(),
                request.operationId(),
                request.prompt(),
                request.model(),
                request.reasoning(),
                request.outputFormat(),
                request.outputSchema(),
                request.imageInputs().isEmpty() ? "" : request.imageInputs().get(0), request.ragSearchContext(), request.subtasks()));
        if (result instanceof AiExecutionSuccess success) {
            devLog("codex_execution_complete", new RequestContext(request.requestId(), request.operationId()),
                    "success", success.finalText() == null ? 0 : success.finalText().length(), started);
            return new AgentExecutionResponse(request.requestId(), success.finalText(), null, success.usage());
        }
        AiExecutionFailure failure = (AiExecutionFailure) result;
        devLog("codex_execution_complete", new RequestContext(request.requestId(), request.operationId()),
                failure.reason().name(), null, started);
        LOGGER.warn("Codex execution failed requestId={} reason={}", request.requestId(), failure.reason());
        String failureType = switch (failure.reason()) {
            case CONNECTION_UNAVAILABLE -> "NO_CONNECTION";
            case CONNECTION_REQUIRED -> "CONNECTION_REQUIRED";
            case REAUTH_REQUIRED -> "REAUTH_REQUIRED";
            case CONNECTION_LOST -> "CONNECTION_LOST";
            case TIMEOUT -> "TIMEOUT";
            case OUTPUT_LIMIT -> "OUTPUT_LIMIT";
            default -> "REMOTE_FAILURE";
        };
        return new AgentExecutionResponse(request.requestId(), "", failureType, null);
    }

    private CompletableFuture<Void> sendResponse(Object response) {
        WebSocket current = socket.get();
        if (current == null) {
            return failedFuture(new IllegalStateException("WebSocket agent is not connected"));
        }
        try {
            return current.sendText(objectMapper.writeValueAsString(response), true)
                    .thenApply(ignored -> null);
        } catch (JsonProcessingException exception) {
            return failedFuture(exception);
        }
    }

    private JsonNode requestRagSearch(String requestId, String query) throws Exception {
        String callId = UUID.randomUUID().toString();
        CompletableFuture<JsonNode> pending = new CompletableFuture<>();
        toolCalls.put(callId, pending);
        try {
            WebSocket current = socket.get();
            if (current == null) throw new IllegalStateException("agent WebSocket is disconnected");
            String message = objectMapper.writeValueAsString(java.util.Map.of(
                    "type", "mcp_tool_call", "requestId", requestId, "callId", callId,
                    "tool", "search_rules", "arguments", java.util.Map.of("query", query)));
            current.sendText(message, true).get(10, TimeUnit.SECONDS);
            return pending.get(150, TimeUnit.SECONDS);
        } finally {
            toolCalls.remove(callId);
        }
    }

    private void acceptToolResult(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String callId = node.path("callId").asText("");
            CompletableFuture<JsonNode> pending = toolCalls.get(callId);
            if (pending == null) return;
            pending.complete(node);
        } catch (Exception invalid) {
            LOGGER.warn("discarded invalid MCP tool result: {}", safeMessage(invalid));
        }
    }

    private void enqueue(String message) {
        executionTail.updateAndGet(previous -> previous.handle((ignored, failure) -> null)
                .thenCompose(ignored -> executeAndReply(message)));
    }

    private static URI requireWebSocketUri(URI value) {
        Objects.requireNonNull(value, "relay endpoint must not be null");
        if (!"ws".equalsIgnoreCase(value.getScheme()) && !"wss".equalsIgnoreCase(value.getScheme())) {
            throw new IllegalArgumentException("relay endpoint must use ws or wss");
        }
        return value;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }

    private static String optionalConnectionId(String value) {
        if (value == null || value.isBlank()) return "";
        return UUID.fromString(value.trim()).toString();
    }

    private static String safeMessage(Throwable failure) {
        return "local operation failed";
    }

    private static <T> CompletableFuture<T> failedFuture(Throwable failure) {
        CompletableFuture<T> result = new CompletableFuture<>();
        result.completeExceptionally(failure);
        return result;
    }

    private final class Listener implements WebSocket.Listener {
        private final StringBuilder fragments = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            if (!socket.compareAndSet(null, webSocket)) {
                webSocket.abort();
                return;
            }
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            fragments.append(data);
            if (last) {
                String message = fragments.toString();
                fragments.setLength(0);
                try {
                    JsonNode node = objectMapper.readTree(message);
                    if ("mcp_tool_result".equals(node.path("type").asText())) acceptToolResult(message);
                    else enqueue(message);
                } catch (JsonProcessingException invalid) {
                    enqueue(message);
                }
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            socket.compareAndSet(webSocket, null);
            closed.complete(null);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            socket.compareAndSet(webSocket, null);
            closed.completeExceptionally(error);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AgentExecutionRequest(
            UUID soloPlayerId,
            String requestId,
            String operationId,
            String prompt,
            String model,
            String reasoning,
            String outputFormat,
            JsonNode outputSchema,
            List<String> imageInputs,
            long deadlineEpochMillis,
            String connectionId,
            JsonNode ragSearchContext,
            List<AiExecutionRequest.Subtask> subtasks) {
        AgentExecutionRequest {
            imageInputs = imageInputs == null ? List.of() : List.copyOf(imageInputs);
            subtasks = subtasks == null ? List.of() : List.copyOf(subtasks);
        }
        AgentExecutionRequest(UUID soloPlayerId, String requestId, String operationId, String prompt, String model,
                String reasoning, String outputFormat, JsonNode outputSchema, List<String> imageInputs,
                long deadlineEpochMillis, String connectionId, JsonNode ragSearchContext) {
            this(soloPlayerId, requestId, operationId, prompt, model, reasoning, outputFormat, outputSchema,
                    imageInputs, deadlineEpochMillis, connectionId, ragSearchContext, List.of());
        }
    }

    record AgentExecutionResponse(String requestId, String content, String failureType,
            com.dndmaster.aigamemaster.application.ai.AiExecutionUsage usage) {}
}
