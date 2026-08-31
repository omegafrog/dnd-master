package com.dndmaster.aigamemaster.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class CodexAppServerClientTest {
    @Test
    void estimatesTokensAndSanitizesMetadataWithoutPayloads() {
        assertThat(AiCallObservability.estimatedTokens(0)).isZero();
        assertThat(AiCallObservability.estimatedTokens(7)).isEqualTo(2);
        assertThat(AiCallObservability.safe("model with secret/prompt")).isEqualTo("model_with_secret_prompt");
        assertThat(AiCallObservability.safe("PRIVATE_PROMPT_DO_NOT_LOG")).doesNotContain(" ");
    }
    @Test
    void boundsCompletionWhenAppServerNeverAnswersThreadStart() throws Exception {
        Path executable = appServerScript("""
                #!/usr/bin/env bash
                while IFS= read -r line; do
                  case "$line" in
                    *'\"method\":\"initialize\"'*) echo '{\"id\":1,\"result\":{}}';;
                  esac
                done
                """);
        CodexAppServerClient client = CodexAppServerClient.shared(
                executable.toString(), executable.getParent(), Duration.ofMillis(150), new ObjectMapper());
        long started = System.nanoTime();
        try {
            assertThatThrownBy(() -> client.complete("timeout", "complete", "gpt-5.6-luna"))
                    .isInstanceOf(ProviderTimeoutException.class);
            assertThat(elapsedMillis(started)).isLessThan(1_000L);
        } finally {
            client.close();
        }
    }

    @Test
    void mapsAppServerProcessExitToProviderFailure() throws Exception {
        Path executable = appServerScript("""
                #!/usr/bin/env bash
                IFS= read -r line
                echo '{\"id\":1,\"result\":{}}'
                exit 23
                """);
        CodexAppServerClient client = CodexAppServerClient.shared(
                executable.toString(), executable.getParent(), Duration.ofSeconds(2), new ObjectMapper());
        try {
            assertThatThrownBy(() -> client.complete("process-exit", "complete", "gpt-5.6-luna"))
                    .isInstanceOf(ProviderTimeoutException.class);
        } finally {
            client.close();
        }
    }

    @Test
    void surfacesV2TurnCompletedErrorInsteadOfReportingMissingText() throws Exception {
        Path executable = appServerScript("""
                #!/usr/bin/env bash
                request=0
                while IFS= read -r line; do
                  case "$line" in
                    *'"method":"initialize"'*) echo '{"id":1,"result":{}}';;
                    *'"method":"thread/start"'*) echo '{"id":2,"result":{"thread":{"id":"thread-1"}}}';;
                    *'"method":"turn/start"'*)
                      echo '{"id":3,"result":{"turn":{"id":"turn-1","status":"inProgress"}}}';
                      echo '{"method":"turn/completed","params":{"turn":{"status":"failed","error":{"codexErrorInfo":"unauthorized","message":"login required"}}}}';;
                  esac
                done
                """);
        CodexAppServerClient client = CodexAppServerClient.shared(
                executable.toString(), executable.getParent(), Duration.ofSeconds(2), new ObjectMapper());
        try {
            assertThatThrownBy(() -> client.complete("turn-error", "complete", "gpt-5.6-luna"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Codex turn failed")
                    .hasMessageContaining("unauthorized");
        } finally {
            client.close();
        }
    }

    private static Path appServerScript(String body) throws Exception {
        Path directory = Files.createTempDirectory("codex-app-server-test");
        Path script = directory.resolve("codex");
        Files.writeString(script, body);
        assertThat(script.toFile().setExecutable(true)).isTrue();
        return script;
    }

    private static long elapsedMillis(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }
}
