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
        return CompletableFuture.supplyAsync(() -> parseMessage(message), executionExecutor)
                .thenApply(this::dispatch)
                .thenCompose(this::sendResponse)
                .exceptionally(failure -> {
                    LOGGER.warn("user-PC agent request failed");
                    return null;
                });
    }

    private Object parseMessage(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            if ("CONNECTION_CONTROL".equals(root.path("messageType").asText())) {
                return objectMapper.treeToValue(root, AgentConnectionControlCommand.class);
            }
            return objectMapper.treeToValue(root, AgentExecutionRequest.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("relay request could not be decoded");
        }
    }

    private Object dispatch(Object message) {
        if (message instanceof AgentConnectionControlCommand command) return control(command);
        return execute((AgentExecutionRequest) message);
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

    private AgentExecutionResponse execute(AgentExecutionRequest request) {
        AiExecutionResult result = executionPort.execute(new AiExecutionRequest(
                request.soloPlayerId(),
                request.requestId(),
                request.operationId(),
                request.prompt(),
                request.model(),
                request.reasoning(),
                request.outputFormat(),
                request.outputSchema(),
                request.imageInputs().isEmpty() ? "" : request.imageInputs().get(0)));
        if (result instanceof AiExecutionSuccess success) {
            return new AgentExecutionResponse(request.requestId(), success.finalText(), null, success.usage());
        }
        AiExecutionFailure failure = (AiExecutionFailure) result;
        LOGGER.warn("Codex execution failed requestId={} reason={}", request.requestId(), failure.reason());
        return new AgentExecutionResponse(request.requestId(), "", "REMOTE_FAILURE", null);
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
                enqueue(message);
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
    private record AgentExecutionRequest(
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
            String connectionId) {
        private AgentExecutionRequest {
            imageInputs = imageInputs == null ? List.of() : List.copyOf(imageInputs);
        }
    }

    private record AgentExecutionResponse(String requestId, String content, String failureType,
            com.dndmaster.aigamemaster.application.ai.AiExecutionUsage usage) {}
}
