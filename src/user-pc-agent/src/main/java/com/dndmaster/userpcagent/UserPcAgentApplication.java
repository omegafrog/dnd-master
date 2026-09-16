package com.dndmaster.userpcagent;

import com.dndmaster.aigamemaster.localcodex.CodexWebSocketAgent;
import com.dndmaster.aigamemaster.localcodex.LocalCodexAiExecutionPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

/** Starts the user PC agent and keeps its authenticated relay connection alive. */
public final class UserPcAgentApplication {
    private UserPcAgentApplication() {}

    public static void main(String[] args) {
        Settings settings = Settings.fromEnvironment();
        ObjectMapper objectMapper = new ObjectMapper();
        LocalCodexAiExecutionPort codex = new LocalCodexAiExecutionPort(
                settings.codexExecutable(),
                settings.codexWorkDirectory(),
                settings.codexTimeout(),
                objectMapper);

        try (codex; CodexWebSocketAgent agent = new CodexWebSocketAgent(
                settings.relayWebSocketUrl(),
                settings.accessToken(),
                codex,
                objectMapper)) {
            Runtime.getRuntime().addShutdownHook(new Thread(agent::close, "user-pc-agent-shutdown"));
            agent.connect().toCompletableFuture().join();
            System.out.println("사용자 PC 에이전트 연결됨: " + settings.relayWebSocketUrl());
            agent.completion().toCompletableFuture().join();
        }
    }

    private record Settings(
            URI relayWebSocketUrl,
            String accessToken,
            String codexExecutable,
            Path codexWorkDirectory,
            Duration codexTimeout) {
        private static Settings fromEnvironment() {
            return new Settings(
                    URI.create(required("RELAY_WEBSOCKET_URL")),
                    required("AGENT_ACCESS_TOKEN"),
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
