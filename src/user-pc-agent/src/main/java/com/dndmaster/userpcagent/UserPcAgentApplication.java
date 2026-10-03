package com.dndmaster.userpcagent;

import com.dndmaster.aigamemaster.localcodex.CodexWebSocketAgent;
import com.dndmaster.aigamemaster.localcodex.CodexConnectionService;
import com.dndmaster.aigamemaster.localcodex.FileInstallationLinkStore;
import com.dndmaster.aigamemaster.localcodex.LocalCodexAiExecutionPort;
import com.dndmaster.aigamemaster.infrastructure.ai.CodexAppServerClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Executors;

/** Starts the user PC agent and keeps its authenticated relay connection alive. */
public final class UserPcAgentApplication {
    private UserPcAgentApplication() {}

    public static void main(String[] args) {
        Settings settings = Settings.fromEnvironment();
        ObjectMapper objectMapper = new ObjectMapper();
        CodexAppServerClient appServer = CodexAppServerClient.shared(settings.codexExecutable(),
                settings.codexWorkDirectory(), settings.codexTimeout(), objectMapper);
        LocalCodexAiExecutionPort codex = new LocalCodexAiExecutionPort(
                settings.codexExecutable(),
                settings.codexWorkDirectory(),
                settings.codexTimeout(),
                objectMapper);

        try (codex; var loginExecutor = Executors.newVirtualThreadPerTaskExecutor();
                CodexWebSocketAgent agent = new CodexWebSocketAgent(
                settings.relayWebSocketUrl(),
                settings.accessToken(),
                settings.connectionId(),
                codex,
                objectMapper,
                java.util.concurrent.ForkJoinPool.commonPool(),
                new CodexConnectionService(appServer, FileInstallationLinkStore.forCurrentUser(),
                        loginExecutor, Duration.ofMinutes(15), Duration.ofSeconds(2)))) {
            Runtime.getRuntime().addShutdownHook(new Thread(agent::close, "user-pc-agent-shutdown"));
            agent.connect().toCompletableFuture().join();
            System.out.println("사용자 PC 에이전트 연결됨: " + settings.relayWebSocketUrl());
            agent.completion().toCompletableFuture().join();
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
