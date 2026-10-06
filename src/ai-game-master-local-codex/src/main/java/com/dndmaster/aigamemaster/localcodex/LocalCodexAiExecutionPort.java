package com.dndmaster.aigamemaster.localcodex;

import com.dndmaster.aigamemaster.application.ai.AiExecutionFailure;
import com.dndmaster.aigamemaster.application.ai.AiExecutionPort;
import com.dndmaster.aigamemaster.application.ai.AiExecutionRequest;
import com.dndmaster.aigamemaster.application.ai.AiExecutionResult;
import com.dndmaster.aigamemaster.application.ai.AiExecutionSuccess;
import com.dndmaster.aigamemaster.infrastructure.ai.CodexAppServerClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Executes Codex through the local app-server and bridges its MCP search tool over the agent WebSocket. */
public final class LocalCodexAiExecutionPort implements AiExecutionPort, AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalCodexAiExecutionPort.class);

    private final CodexAppServerClient client;
    private final AtomicReference<String> activeRequestId = new AtomicReference<>();
    private final McpRelayServer relay;
    private volatile RagSearchHandler ragSearchHandler = (requestId, query) -> {
        throw new IllegalStateException("룰북 검색 중계가 준비되지 않았습니다.");
    };

    public LocalCodexAiExecutionPort(String executable, Path workDirectory, Duration timeout, ObjectMapper mapper) {
        relay = new McpRelayServer(activeRequestId, () -> ragSearchHandler, mapper);
        relay.start();
        client = CodexAppServerClient.shared(executable, workDirectory, timeout, mapper, relay.configOverrides());
    }

    public CodexAppServerClient appServerClient() {
        return client;
    }

    public void setRagSearchHandler(RagSearchHandler handler) {
        ragSearchHandler = java.util.Objects.requireNonNull(handler);
    }

    @Override
    public synchronized AiExecutionResult execute(AiExecutionRequest request) {
        String requestId = request.ragSearchContext() == null || request.ragSearchContext().isNull()
                ? null : request.requestId();
        activeRequestId.set(requestId);
        try {
            var completion = client.completeWithUsage(request.requestId(), request.completedPrompt(), request.model(),
                    request.reasoning(), request.outputSchema(), request.imageDataUri());
            return new AiExecutionSuccess(completion.finalText(), completion.usage());
        } catch (com.dndmaster.aigamemaster.infrastructure.ai.CodexAuthenticationRejectedException rejected) {
            return new AiExecutionFailure(AiExecutionFailure.Reason.REAUTH_REQUIRED,
                    "Codex 계정 인증을 다시 해야 합니다.");
        } catch (com.dndmaster.aigamemaster.infrastructure.ai.CodexTurnTimeoutException timeout) {
            return new AiExecutionFailure(AiExecutionFailure.Reason.TIMEOUT, timeout.getMessage());
        } catch (RuntimeException failure) {
            return new AiExecutionFailure(AiExecutionFailure.Reason.LOCAL_EXECUTION_FAILED,
                    failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
        } finally {
            activeRequestId.set(null);
        }
    }

    @Override
    public void close() {
        client.close();
        relay.close();
    }

    private static String toml(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String javaExecutable() {
        return Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java").toString();
    }

    private static final class McpRelayServer implements AutoCloseable {
        private final AtomicReference<String> activeRequestId;
        private final java.util.function.Supplier<RagSearchHandler> handler;
        private final ObjectMapper mapper;
        private final ServerSocket server;
        private final String token = UUID.randomUUID().toString();
        private final AtomicBoolean closed = new AtomicBoolean();

        private McpRelayServer(AtomicReference<String> activeRequestId,
                java.util.function.Supplier<RagSearchHandler> handler, ObjectMapper mapper) {
            this.activeRequestId = activeRequestId;
            this.handler = handler;
            this.mapper = mapper;
            try {
                server = new ServerSocket(0, 8, java.net.InetAddress.getByName("127.0.0.1"));
            } catch (IOException failure) {
                throw new IllegalStateException("로컬 룰북 검색 중계를 시작하지 못했습니다.", failure);
            }
        }

        private List<String> configOverrides() {
            List<String> args = List.of("-cp", System.getProperty("java.class.path"),
                    McpStdioServer.class.getName(), "127.0.0.1", Integer.toString(server.getLocalPort()), token);
            return List.of(
                    "mcp_servers.rag_search.command=" + toml(javaExecutable()),
                    "mcp_servers.rag_search.args=" + tomlArray(args),
                    "mcp_servers.rag_search.enabled=true",
                    "mcp_servers.rag_search.required=true",
                    "mcp_servers.rag_search.default_tools_approval_mode=\"auto\"",
                    "mcp_servers.rag_search.tools.search_rules.approval_mode=\"auto\"",
                    "mcp_servers.rag_search.enabled_tools=[\"search_rules\"]",
                    "mcp_servers.rag_search.startup_timeout_sec=20",
                    "mcp_servers.rag_search.tool_timeout_sec=150");
        }

        private static String tomlArray(List<String> values) {
            return "[" + String.join(",", values.stream().map(LocalCodexAiExecutionPort::toml).toList()) + "]";
        }

        private void start() {
            Thread listener = new Thread(this::acceptClients, "local-mcp-websocket-bridge");
            listener.setDaemon(true);
            listener.start();
        }

        private void acceptClients() {
            while (!closed.get()) {
                try {
                    Socket client = server.accept();
                    Thread.startVirtualThread(() -> serve(client));
                } catch (IOException failure) {
                    if (!closed.get()) LOGGER.warn("local MCP bridge accept failed: {}", failure.getClass().getSimpleName());
                }
            }
        }

        private void serve(Socket socket) {
            try (socket;
                    BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    BufferedWriter output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
                if (!token.equals(input.readLine())) return;
                String line;
                while (!closed.get() && (line = input.readLine()) != null) {
                    output.write(mapper.writeValueAsString(search(line)));
                    output.newLine();
                    output.flush();
                }
            } catch (Exception failure) {
                if (!closed.get()) LOGGER.warn("local MCP bridge request failed: {}", failure.getClass().getSimpleName());
            }
        }

        private JsonNode search(String line) throws Exception {
            JsonNode call = mapper.readTree(line);
            String requestId = activeRequestId.get();
            if (requestId == null || requestId.isBlank()) {
                return mapper.createObjectNode().put("success", false)
                        .put("error", "현재 요청에 허용된 룰북 검색 범위가 없습니다.");
            }
            try {
                JsonNode result = handler.get().search(requestId, call.path("query").asText());
                return mapper.createObjectNode().put("success", true).set("result", result);
            } catch (Exception failure) {
                return mapper.createObjectNode().put("success", false).put("error", "룰북 검색을 완료하지 못했습니다.");
            }
        }

        @Override
        public void close() {
            closed.set(true);
            try {
                server.close();
            } catch (IOException ignored) {
            }
        }
    }

    @FunctionalInterface
    public interface RagSearchHandler {
        JsonNode search(String requestId, String query) throws Exception;
    }
}
