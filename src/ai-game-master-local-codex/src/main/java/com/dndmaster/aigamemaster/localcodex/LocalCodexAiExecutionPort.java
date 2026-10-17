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
    private volatile McpToolHandler mcpToolHandler = (requestId, tool, query) -> {
        throw new IllegalStateException("MCP 도구 중계가 준비되지 않았습니다.");
    };

    public LocalCodexAiExecutionPort(String executable, Path workDirectory, Duration timeout, ObjectMapper mapper) {
        this(executable, workDirectory, timeout, mapper, false);
    }

    public LocalCodexAiExecutionPort(String executable, Path workDirectory, Duration timeout, ObjectMapper mapper,
            boolean installClientMcpConfig) {
        relay = new McpRelayServer(activeRequestId, () -> mcpToolHandler, mapper, workDirectory, installClientMcpConfig);
        try {
            if (installClientMcpConfig) relay.installConfig();
            client = CodexAppServerClient.shared(executable, workDirectory, timeout, mapper, relay.configOverrides());
            relay.start();
        } catch (RuntimeException failure) {
            relay.close();
            throw failure;
        }
    }

    public CodexAppServerClient appServerClient() {
        return client;
    }

    public void setRagSearchHandler(RagSearchHandler handler) {
        java.util.Objects.requireNonNull(handler);
        mcpToolHandler = (requestId, tool, query) -> {
            if (!"search_rules".equals(tool)) throw new IllegalArgumentException("unsupported MCP tool");
            return handler.search(requestId, query);
        };
    }

    public void setMcpToolHandler(McpToolHandler handler) {
        mcpToolHandler = java.util.Objects.requireNonNull(handler);
    }

    @Override
    public synchronized AiExecutionResult execute(AiExecutionRequest request) {
        String requestId = request.ragSearchContext() == null || request.ragSearchContext().isNull()
                ? null : request.requestId();
        activeRequestId.set(requestId);
        try {
            var completion = client.completeWithUsage(request.requestId(), request.completedPrompt(), request.model(),
                    request.reasoning(), request.outputSchema(), request.imageDataUri(), request.subtasks());
            return new AiExecutionSuccess(completion.finalText(), completion.usage());
        } catch (com.dndmaster.aigamemaster.infrastructure.ai.CodexAuthenticationRejectedException rejected) {
            return new AiExecutionFailure(AiExecutionFailure.Reason.REAUTH_REQUIRED,
                    "Codex 계정 인증을 다시 해야 합니다.");
        } catch (com.dndmaster.aigamemaster.infrastructure.ai.CodexTurnTimeoutException timeout) {
            return new AiExecutionFailure(AiExecutionFailure.Reason.TIMEOUT, timeout.getMessage());
        } catch (com.dndmaster.aigamemaster.infrastructure.ai.CodexTurnFailedException failure) {
            if ("OUTPUT_LIMIT".equals(failure.errorCategory()))
                return new AiExecutionFailure(AiExecutionFailure.Reason.OUTPUT_LIMIT, "Codex response output limit reached");
            return new AiExecutionFailure(AiExecutionFailure.Reason.LOCAL_EXECUTION_FAILED, "Codex app-server execution failed");
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
        private final java.util.function.Supplier<McpToolHandler> handler;
        private final ObjectMapper mapper;
        private final ServerSocket server;
        private final Path configPath;
        private final String token = UUID.randomUUID().toString();
        private final AtomicBoolean closed = new AtomicBoolean();

        private McpRelayServer(AtomicReference<String> activeRequestId,
                java.util.function.Supplier<McpToolHandler> handler, ObjectMapper mapper, Path workDirectory,
                boolean installClientMcpConfig) {
            this.activeRequestId = activeRequestId;
            this.handler = handler;
            this.mapper = mapper;
            this.configPath = installClientMcpConfig
                    ? workDirectory.toAbsolutePath().normalize().resolve(".codex/config.toml") : null;
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
                    "mcp_servers.rag_search.tools.find_scenario_elements.approval_mode=\"auto\"",
                    "mcp_servers.rag_search.enabled_tools=[\"search_rules\",\"find_scenario_elements\"]",
                    "mcp_servers.rag_search.startup_timeout_sec=20",
                    "mcp_servers.rag_search.tool_timeout_sec=150");
        }

        private void installConfig() {
            long startedAt = System.nanoTime();
            if (diagnosticsEnabled()) LOGGER.info("dev_codex_workspace stage=install_mcp configPath={} outcome=started", configPath);
            try {
                java.nio.file.Files.createDirectories(configPath.getParent());
                try {
                    java.nio.file.Files.setPosixFilePermissions(configPath.getParent(),
                            java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
                } catch (UnsupportedOperationException ignored) {
                    // The platform does not expose POSIX file permissions.
                }
                List<String> args = List.of("-cp", System.getProperty("java.class.path"),
                        McpStdioServer.class.getName(), "127.0.0.1", Integer.toString(server.getLocalPort()), token);
                String content = "# Generated for this D&D Master client run.\n"
                        + "[mcp_servers.rag_search]\n"
                        + "command = " + toml(javaExecutable()) + "\n"
                        + "args = " + tomlArray(args) + "\n"
                        + "enabled = true\nrequired = true\n"
                        + "default_tools_approval_mode = \"auto\"\n"
                        + "enabled_tools = [\"search_rules\", \"find_scenario_elements\"]\n"
                        + "startup_timeout_sec = 20\ntool_timeout_sec = 150\n"
                        + "[mcp_servers.rag_search.tools.search_rules]\napproval_mode = \"auto\"\n"
                        + "[mcp_servers.rag_search.tools.find_scenario_elements]\napproval_mode = \"auto\"\n";
                Path temporary;
                try {
                    temporary = java.nio.file.Files.createTempFile(configPath.getParent(), "config-", ".tmp",
                            java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                                    java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")));
                } catch (UnsupportedOperationException ignored) {
                    temporary = java.nio.file.Files.createTempFile(configPath.getParent(), "config-", ".tmp");
                }
                try {
                    java.nio.file.Files.writeString(temporary, content, StandardCharsets.UTF_8);
                    try {
                        java.nio.file.Files.move(temporary, configPath, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                        java.nio.file.Files.move(temporary, configPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                } finally {
                    java.nio.file.Files.deleteIfExists(temporary);
                }
                if (diagnosticsEnabled()) LOGGER.info("dev_codex_workspace stage=install_mcp configChars={} elapsedMs={} outcome=completed",
                        content.length(), (System.nanoTime() - startedAt) / 1_000_000);
            } catch (IOException | RuntimeException failure) {
                if (diagnosticsEnabled()) LOGGER.warn("dev_codex_workspace stage=install_mcp configPath={} failureType={} elapsedMs={} recoveryAction=abort_client_start outcome=failed",
                        configPath, failure.getClass().getSimpleName(), (System.nanoTime() - startedAt) / 1_000_000);
                throw new IllegalStateException("Codex 전용 작업 디렉터리에 MCP 설정을 설치하지 못했습니다.", failure);
            }
        }

        private static boolean diagnosticsEnabled() {
            return "true".equalsIgnoreCase(System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED",
                    System.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED")));
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
                    output.write(mapper.writeValueAsString(callTool(line)));
                    output.newLine();
                    output.flush();
                }
            } catch (Exception failure) {
                if (!closed.get()) LOGGER.warn("local MCP bridge request failed: {}", failure.getClass().getSimpleName());
            }
        }

        private JsonNode callTool(String line) throws Exception {
            JsonNode call = mapper.readTree(line);
            String requestId = activeRequestId.get();
            if (requestId == null || requestId.isBlank()) {
                return mapper.createObjectNode().put("success", false)
                        .put("error", "현재 요청에 허용된 자료 조회 범위가 없습니다.");
            }
            try {
                JsonNode result = handler.get().call(requestId, call.path("tool").asText("search_rules"), call.path("query").asText());
                return mapper.createObjectNode().put("success", true).set("result", result);
            } catch (Exception failure) {
                return mapper.createObjectNode().put("success", false).put("error", "자료 조회 도구를 실행하지 못했습니다.");
            }
        }

        @Override
        public void close() {
            closed.set(true);
            try {
                server.close();
            } catch (IOException ignored) {
            }
            try {
                if (configPath != null) java.nio.file.Files.deleteIfExists(configPath);
            } catch (IOException failure) {
                if (diagnosticsEnabled()) LOGGER.warn("dev_codex_workspace stage=remove_mcp_config configPath={} failureType={} recoveryAction=manual_cleanup outcome=failed",
                        configPath, failure.getClass().getSimpleName());
            }
        }
    }

    @FunctionalInterface
    public interface RagSearchHandler {
        JsonNode search(String requestId, String query) throws Exception;
    }

    @FunctionalInterface
    public interface McpToolHandler {
        JsonNode call(String requestId, String tool, String query) throws Exception;
    }
}
