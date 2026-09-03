package com.dndmaster.aigamemaster.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class CodexAppServerClientTest {
    @Test
    void classifiesScenarioCompilationResolutionCallsSeparatelyFromStoryPlanAuthoring() {
        assertThat(CodexAppServerClient.stage("scenario-compilation:abc:resolution-candidates"))
                .isEqualTo("resolution-candidate-extraction");
        assertThat(CodexAppServerClient.stage("scenario-compilation:abc:resolution-candidate-repair"))
                .isEqualTo("resolution-candidate-repair");
        assertThat(CodexAppServerClient.stage("story-plan-123"))
                .isEqualTo("story-plan-generation");
    }

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
                    .isInstanceOf(CodexTurnTimeoutException.class)
                    .satisfies(error -> {
                        var timeout = (CodexTurnTimeoutException) error;
                        assertThat(timeout.operationId()).isEqualTo("timeout");
                        assertThat(timeout.phase()).isEqualTo("story-plan-generation");
                        assertThat(timeout.timeoutMillis()).isEqualTo(150L);
                        assertThat(timeout.lastEvent()).isEqualTo("request");
                    });
            assertThat(elapsedMillis(started)).isLessThan(1_000L);
        } finally {
            client.close();
        }
    }

    @Test
    void servesDynamicToolCallsBackToTheAppServer() throws Exception {
        Path executable = appServerScript("""
                #!/usr/bin/env bash
                while IFS= read -r line; do
                  case "$line" in
                    *'\"method\":\"initialize\"'*) echo '{\"id\":1,\"result\":{}}';;
                    *'\"method\":\"thread/start\"'*) echo '{\"id\":2,\"result\":{\"thread\":{\"id\":\"thread-1\"}}}';;
                    *'\"method\":\"turn/start\"'*)
                      echo '{\"id\":3,\"result\":{\"turn\":{\"id\":\"turn-1\"}}}';
                      echo '{\"id\":4,\"method\":\"item/tool/call\",\"params\":{\"threadId\":\"thread-1\",\"turnId\":\"turn-1\",\"callId\":\"call-1\",\"tool\":\"lookup\",\"arguments\":{\"query\":\"rat\"}}}';;
                    *'\"id\":4,\"result\"'*)
                      echo '{\"method\":\"item/agentMessage/delta\",\"params\":{\"delta\":\"done\"}}';
                      echo '{\"method\":\"turn/completed\",\"params\":{\"turn\":{\"status\":\"completed\"}}}';;
                  esac
                done
                """);
        CodexAppServerClient client = CodexAppServerClient.shared(
                executable.toString(), executable.getParent(), Duration.ofSeconds(2), new ObjectMapper());
        ObjectMapper mapper = new ObjectMapper();
        var schema = mapper.createObjectNode().put("type", "object");
        try {
            String result = client.complete("dynamic-tool", "use lookup", "gpt-5.6-luna", "medium", null,
                    List.of(new CodexAppServerClient.DynamicTool(
                            "lookup", "Look up a fact", schema, arguments -> "found:" + arguments.path("query").asText())));
            assertThat(result).isEqualTo("done");
        } finally {
            client.close();
        }
    }

    @Test
    void optsIntoExperimentalApiWhenInitializingDynamicToolSupport() throws Exception {
        Path executable = appServerScript("""
                #!/usr/bin/env bash
                while IFS= read -r line; do
                  case "$line" in
                    *'\"method\":\"initialize\"'*)
                      case "$line" in *'\"experimentalApi\":true'*) touch experimental-api-enabled;; esac
                      echo '{\"id\":1,\"result\":{}}';;
                    *'\"method\":\"thread/start\"'*) echo '{\"id\":2,\"result\":{\"thread\":{\"id\":\"thread-1\"}}}';;
                    *'\"method\":\"turn/start\"'*)
                      echo '{\"id\":3,\"result\":{\"turn\":{\"id\":\"turn-1\"}}}';
                      echo '{\"method\":\"item/agentMessage/delta\",\"params\":{\"delta\":\"done\"}}';
                      echo '{\"method\":\"turn/completed\",\"params\":{\"turn\":{\"status\":\"completed\"}}}';;
                  esac
                done
                """);
        CodexAppServerClient client = CodexAppServerClient.shared(
                executable.toString(), executable.getParent(), Duration.ofSeconds(2), new ObjectMapper());
        ObjectMapper mapper = new ObjectMapper();
        var schema = mapper.createObjectNode().put("type", "object");
        try {
            assertThat(client.complete("experimental-api", "use lookup", "gpt-5.6-luna", "medium", null,
                    List.of(new CodexAppServerClient.DynamicTool(
                            "lookup", "Look up a fact", schema, arguments -> "found"))))
                    .isEqualTo("done");
            assertThat(Files.exists(executable.getParent().resolve("experimental-api-enabled"))).isTrue();
        } finally {
            client.close();
        }
    }

    @Test
    void boundsRepeatedDynamicToolCallsBeforeTheyCanExtendTheTurnIndefinitely() throws Exception {
        Path executable = appServerScript("""
                #!/usr/bin/env bash
                while IFS= read -r line; do
                  case "$line" in
                    *'\"method\":\"initialize\"'*) echo '{\"id\":1,\"result\":{}}';;
                    *'\"method\":\"thread/start\"'*) echo '{\"id\":2,\"result\":{\"thread\":{\"id\":\"thread-1\"}}}';;
                    *'\"method\":\"turn/start\"'*)
                      echo '{\"id\":3,\"result\":{\"turn\":{\"id\":\"turn-1\"}}}';
                      for i in $(seq 1 30); do
                        echo \"{\\\"id\\\":$((100+i)),\\\"method\\\":\\\"item/tool/call\\\",\\\"params\\\":{\\\"tool\\\":\\\"lookup\\\",\\\"arguments\\\":{\\\"query\\\":\\\"rat-$i\\\"}}}\";
                      done
                      echo '{\"method\":\"item/agentMessage/delta\",\"params\":{\"delta\":\"done\"}}';
                      echo '{\"method\":\"turn/completed\",\"params\":{\"turn\":{\"status\":\"completed\"}}}';;
                  esac
                done
                """);
        CodexAppServerClient client = CodexAppServerClient.shared(
                executable.toString(), executable.getParent(), Duration.ofSeconds(2), new ObjectMapper());
        ObjectMapper mapper = new ObjectMapper();
        var schema = mapper.createObjectNode().put("type", "object");
        AtomicInteger calls = new AtomicInteger();
        try {
            assertThat(client.complete("dynamic-tool-budget", "use lookup", "gpt-5.6-luna", "medium", null,
                    List.of(new CodexAppServerClient.DynamicTool(
                            "lookup", "Look up a fact", schema, arguments -> {
                                calls.incrementAndGet();
                                return "found";
                            }))))
                    .isEqualTo("done");
            assertThat(calls).hasValue(CodexAppServerClient.DEFAULT_MAX_DYNAMIC_TOOL_CALLS);
        } finally {
            client.close();
        }
    }

    @Test
    void skipsRepeatedDynamicToolQueriesWithinOneTurn() throws Exception {
        Path executable = appServerScript("""
                #!/usr/bin/env bash
                while IFS= read -r line; do
                  case "$line" in
                    *'\"method\":\"initialize\"'*) echo '{\"id\":1,\"result\":{}}';;
                    *'\"method\":\"thread/start\"'*) echo '{\"id\":2,\"result\":{\"thread\":{\"id\":\"thread-1\"}}}';;
                    *'\"method\":\"turn/start\"'*)
                      echo '{\"id\":3,\"result\":{\"turn\":{\"id\":\"turn-1\"}}}';
                      echo '{\"id\":101,\"method\":\"item/tool/call\",\"params\":{\"tool\":\"lookup\",\"arguments\":{\"query\":\"rat\"}}}';
                      echo '{\"id\":102,\"method\":\"item/tool/call\",\"params\":{\"tool\":\"lookup\",\"arguments\":{\"query\":\" rat \"}}}';
                      echo '{\"method\":\"item/agentMessage/delta\",\"params\":{\"delta\":\"done\"}}';
                      echo '{\"method\":\"turn/completed\",\"params\":{\"turn\":{\"status\":\"completed\"}}}';;
                  esac
                done
                """);
        CodexAppServerClient client = CodexAppServerClient.shared(
                executable.toString(), executable.getParent(), Duration.ofSeconds(2), new ObjectMapper());
        ObjectMapper mapper = new ObjectMapper();
        var schema = mapper.createObjectNode().put("type", "object");
        AtomicInteger calls = new AtomicInteger();
        try {
            assertThat(client.complete("dynamic-tool-duplicate", "use lookup", "gpt-5.6-luna", "medium", null,
                    List.of(new CodexAppServerClient.DynamicTool(
                            "lookup", "Look up a fact", schema, arguments -> {
                                calls.incrementAndGet();
                                return "found";
                            }))))
                    .isEqualTo("done");
            assertThat(calls).hasValue(1);
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

    @Test
    void sendsBestEffortCancelAndDoesNotWaitForCancelAcknowledgement() throws Exception {
        Path executable = appServerScript("""
                #!/usr/bin/env bash
                while IFS= read -r line; do
                  case "$line" in
                    *'\"method\":\"initialize\"'*) echo '{\"id\":1,\"result\":{}}';;
                    *'\"method\":\"thread/start\"'*) echo '{\"id\":2,\"result\":{\"thread\":{\"id\":\"thread-1\"}}}';;
                    *'\"method\":\"turn/start\"'*) echo '{\"id\":3,\"result\":{\"turn\":{\"id\":\"turn-1\"}}}';;
                    *'\"method\":\"turn/cancel\"'*) touch cancel-requested; sleep 5;;
                  esac
                done
                """);
        CodexAppServerClient client = CodexAppServerClient.shared(
                executable.toString(), executable.getParent(), Duration.ofMillis(120), new ObjectMapper());
        try {
            assertThatThrownBy(() -> client.complete("cancel-timeout", "complete", "gpt-5.6-luna"))
                    .isInstanceOf(CodexTurnTimeoutException.class);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while (!Files.exists(executable.getParent().resolve("cancel-requested"))
                    && System.nanoTime() < deadline) Thread.sleep(10);
            assertThat(Files.exists(executable.getParent().resolve("cancel-requested"))).isTrue();
        } finally {
            client.close();
        }
    }

    @Test
    void distinguishesTurnFailedFromDeadlineTimeout() throws Exception {
        Path executable = appServerScript("""
                #!/usr/bin/env bash
                while IFS= read -r line; do
                  case "$line" in
                    *'\"method\":\"initialize\"'*) echo '{\"id\":1,\"result\":{}}';;
                    *'\"method\":\"thread/start\"'*) echo '{\"id\":2,\"result\":{\"thread\":{\"id\":\"thread-1\"}}}';;
                    *'\"method\":\"turn/start\"'*) echo '{\"id\":3,\"result\":{}}'; echo '{\"method\":\"turn/failed\",\"params\":{\"error\":\"bad turn\"}}';;
                  esac
                done
                """);
        CodexAppServerClient client = CodexAppServerClient.shared(
                executable.toString(), executable.getParent(), Duration.ofSeconds(2), new ObjectMapper());
        try {
            assertThatThrownBy(() -> client.complete("turn-failed", "complete", "gpt-5.6-luna"))
                    .isInstanceOf(CodexTurnFailedException.class)
                    .hasMessageContaining("bad turn");
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
