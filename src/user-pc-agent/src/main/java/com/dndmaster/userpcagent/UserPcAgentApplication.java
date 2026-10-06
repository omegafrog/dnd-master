package com.dndmaster.userpcagent;

import com.dndmaster.aigamemaster.localcodex.CodexWebSocketAgent;
import com.dndmaster.aigamemaster.localcodex.LocalCodexAiExecutionPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Starts the user PC agent and keeps its authenticated relay connection alive. */
public final class UserPcAgentApplication {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserPcAgentApplication.class);

    private UserPcAgentApplication() {}

    public static void main(String[] args) {
        Settings settings = Settings.fromEnvironment();
        ObjectMapper objectMapper = new ObjectMapper();
        LocalCodexAiExecutionPort codex = new LocalCodexAiExecutionPort(
                settings.codexExecutable(),
                settings.codexWorkDirectory(),
                settings.codexTimeout(),
                objectMapper);

        try (codex) {
            AtomicBoolean stopping = new AtomicBoolean();
            AtomicBoolean initialConnectionEstablished = new AtomicBoolean();
            AtomicReference<CodexWebSocketAgent> activeAgent = new AtomicReference<>();
            Thread shutdownHook = new Thread(() -> {
                stopping.set(true);
                CodexWebSocketAgent agent = activeAgent.get();
                if (agent != null) agent.close();
            }, "user-pc-agent-shutdown");
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            try {
                AgentConnectionSupervisor.run(
                        () -> {
                            String connectionId = initialConnectionEstablished.get()
                                    ? UUID.randomUUID().toString()
                                    : settings.connectionId();
                            CodexWebSocketAgent agent = new CodexWebSocketAgent(
                                    settings.relayWebSocketUrl(),
                                    settings.accessToken(),
                                    connectionId,
                                    codex,
                                    objectMapper);
                            activeAgent.set(agent);
                            return new AgentConnectionSupervisor.Session() {
                                @Override
                                public void connectAndWait() {
                                    agent.connect().toCompletableFuture().join();
                                    initialConnectionEstablished.set(true);
                                    System.out.println("사용자 PC 에이전트 WebSocket 연결됨: " + settings.relayWebSocketUrl());
                                    agent.completion().toCompletableFuture().join();
                                    if (!stopping.get()) {
                                        throw new IllegalStateException("WebSocket relay 연결이 종료됨");
                                    }
                                }

                                @Override
                                public void close() {
                                    activeAgent.compareAndSet(agent, null);
                                    agent.close();
                                }
                            };
                        },
                        delay -> Thread.sleep(delay.toMillis()),
                        stopping::get,
                        retry -> {
                            Throwable cause = retry.failure();
                            while (cause instanceof CompletionException && cause.getCause() != null) {
                                cause = cause.getCause();
                            }
                            LOGGER.warn("사용자 PC 에이전트 연결이 끊겼습니다. {}ms 후 재연결합니다 (연속 실패 {}회): {}",
                                    retry.delay().toMillis(), retry.consecutiveFailures(),
                                    cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage());
                        });
            } finally {
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                } catch (IllegalStateException ignored) {
                    // JVM shutdown is already in progress.
                }
                CodexWebSocketAgent agent = activeAgent.getAndSet(null);
                if (agent != null) agent.close();
            }
        }
    }

    private record Settings(
            URI relayWebSocketUrl,
            String accessToken,
            String connectionId,
            String codexExecutable,
            Path codexWorkDirectory,
            Duration codexTimeout) {
        private static Settings fromEnvironment() {
            return new Settings(
                    URI.create(required("RELAY_WEBSOCKET_URL")),
                    required("AGENT_ACCESS_TOKEN"),
                    required("AGENT_CONNECTION_ID"),
                    environmentOrDefault("CODEX_EXECUTABLE", "codex"),
                    Path.of(environmentOrDefault("CODEX_WORK_DIRECTORY", "/tmp")),
                    Duration.parse(environmentOrDefault("CODEX_TIMEOUT", "PT5M")));
        }

        private static String required(String name) {
            String value = System.getenv(name);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException(name + " 환경 변수가 필요함");
            }
            return value.trim();
        }

        private static String environmentOrDefault(String name, String defaultValue) {
            String value = System.getenv(name);
            return value == null || value.isBlank() ? defaultValue : value.trim();
        }
    }
}
