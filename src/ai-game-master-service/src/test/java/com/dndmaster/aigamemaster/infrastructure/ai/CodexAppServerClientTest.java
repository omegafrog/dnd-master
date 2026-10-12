package com.dndmaster.aigamemaster.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.JsonNode;
import com.dndmaster.aigamemaster.application.ai.AiExecutionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class CodexAppServerClientTest {
    @Test
    void closesRequestOwnedAppServerAndDescendantsAfterSuccessfulResponse(CapturedOutput output) throws Exception {
        Path directory = Files.createTempDirectory("codex-request-cleanup-test");
        Path childPid = directory.resolve("child.pid");
        Path executable = directory.resolve("codex");
        Files.writeString(executable, """
                #!/usr/bin/env bash
                while IFS= read -r line; do
                  case "$line" in
                    *'"method":"initialize"'*) echo '{"id":1,"result":{}}';;
                    *'"method":"thread/start"'*) echo '{"id":2,"result":{"thread":{"id":"thread-cleanup"}}}';;
                    *'"method":"turn/start"'*)
                      sleep 30 </dev/null >/dev/null 2>&1 &
                      echo $! > CHILD_PID_PATH
                      echo '{"id":3,"result":{"turn":{"id":"turn-cleanup"}}}';
                      echo '{"method":"item/agentMessage/delta","params":{"delta":"{\\"scene\\":\\"hall\\"}"}}';
                      echo '{"method":"turn/completed","params":{"turn":{"id":"turn-cleanup","status":"completed"}}}';;
                  esac
                done
                """.replace("CHILD_PID_PATH", childPid.toString()));
        assertThat(executable.toFile().setExecutable(true)).isTrue();
        CodexAppServerClient client = CodexAppServerClient.shared(
                executable.toString(), directory, Duration.ofSeconds(3), new ObjectMapper());

        assertThat(client.complete("request-cleanup", "prompt", "gpt-5.6-luna")).isEqualTo("{\"scene\":\"hall\"}");
        long pid = Long.parseLong(Files.readString(childPid).trim());
        assertThat(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)).isFalse();
        if ("true".equalsIgnoreCase(System.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED"))) {
            assertThat(output).contains("dev_codex_app_server_process outcome=request_closed")
                    .contains("expectedChildCount=1").contains("actualAliveChildCount=0");
        }
    }

    @Test
    void closesRequestOwnedDescendantAfterAppServerIoFailure() throws Exception {
        assertChildProcessClosed("io-failure", """
                *'"method":"turn/start"'*)
                  sleep 30 </dev/null >/dev/null 2>&1 &
                  echo $! > CHILD_PID_PATH
                  echo '{"id":3,"result":{"turn":{"id":"turn-io"}}}'
                  exec 1>&-
                  sleep 30;;
                """);
    }

    @Test
    void closesRequestOwnedDescendantAfterTurnTimeout() throws Exception {
        assertChildProcessClosed("timeout", """
                *'"method":"turn/start"'*)
                  sleep 30 </dev/null >/dev/null 2>&1 &
                  echo $! > CHILD_PID_PATH
                  echo '{"id":3,"result":{"turn":{"id":"turn-timeout"}}}';;
                """);
    }

    private static void assertChildProcessClosed(String mode, String turnStartCase) throws Exception {
        Path directory = Files.createTempDirectory("codex-request-cleanup-" + mode);
        Path childPid = directory.resolve("child.pid");
        Path executable = directory.resolve("codex");
        Files.writeString(executable, ("""
                #!/usr/bin/env bash
                while IFS= read -r line; do
                  case "$line" in
                    *'"method":"initialize"'*) echo '{"id":1,"result":{}}';;
                    *'"method":"thread/start"'*) echo '{"id":2,"result":{"thread":{"id":"thread-cleanup"}}}';;
                    TURN_START_CASE
                  esac
                done
                """).replace("TURN_START_CASE", turnStartCase).replace("CHILD_PID_PATH", childPid.toString()));
        assertThat(executable.toFile().setExecutable(true)).isTrue();
        Duration timeout = mode.equals("timeout") ? Duration.ofMillis(500) : Duration.ofSeconds(3);
        CodexAppServerClient client = CodexAppServerClient.shared(executable.toString(), directory, timeout, new ObjectMapper());

        assertThatThrownBy(() -> client.complete("request-cleanup-" + mode, "prompt", "gpt-5.6-luna"))
                .isInstanceOf(mode.equals("timeout") ? CodexTurnTimeoutException.class : ProviderTimeoutException.class);
        long pid = Long.parseLong(Files.readString(childPid).trim());
        assertThat(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)).isFalse();
    }

    @Test
    void selectsFinalAnswerInsteadOfPrecedingCommentary(CapturedOutput output) throws Exception {
        String finalJson = "{\"scene\":\"hall\"}";
        Path executable = appServerScript("""
                #!/usr/bin/env bash
                while IFS= read -r line; do
                  case "$line" in
                    *'"method":"initialize"'*) echo '{"id":1,"result":{}}';;
                    *'"method":"thread/start"'*) echo '{"id":2,"result":{"thread":{"id":"thread-phase"}}}';;
                    *'"method":"turn/start"'*)
                      echo '{"id":3,"result":{"turn":{"id":"turn-phase"}}}';
                      echo '{"method":"item/agentMessage/delta","params":{"threadId":"thread-phase","turnId":"turn-phase","itemId":"message-commentary","delta":"Brief commentary before the answer."}}';
                      echo '{"method":"item/completed","params":{"threadId":"thread-phase","turnId":"turn-phase","completedAtMs":1,"item":{"type":"agentMessage","id":"message-commentary","text":"Brief commentary before the answer.","phase":"commentary"}}}';
                      echo '{"method":"item/agentMessage/delta","params":{"threadId":"thread-phase","turnId":"turn-phase","itemId":"message-final","delta":"{\\"scene\\":\\"hall\\"}"}}';
                      echo '{"method":"item/completed","params":{"threadId":"thread-phase","turnId":"turn-phase","completedAtMs":2,"item":{"type":"agentMessage","id":"message-final","text":"{\\"scene\\":\\"hall\\"}","phase":"final_answer"}}}';
                      echo '{"method":"turn/completed","params":{"turn":{"id":"turn-phase","status":"completed"}}}';;
                  esac
                done
                """);
        CodexAppServerClient client = CodexAppServerClient.shared(
                executable.toString(), executable.getParent(), Duration.ofSeconds(3), new ObjectMapper());
        try {
            assertThat(client.complete("phase-selection", "private prompt", "gpt-5.6-luna")).isEqualTo(finalJson);
            if ("true".equalsIgnoreCase(System.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED"))) {
                assertThat(output).contains("dev_codex_agent_message outcome=completed")
                        .contains("phase=commentary").contains("phase=final_answer")
                        .contains("messageCount=1").contains("messageCount=2")
                        .contains("completedJsonObject=true").contains("completedFields=[scene]")
                        .contains("selectionStrategy=latest_final_answer_item")
                        .contains("responseFingerprint=")
                        .doesNotContain("Brief commentary before the answer.").doesNotContain("private prompt");
            } else {
                assertThat(output).doesNotContain("dev_codex_agent_message");
            }
        } finally { client.close(); }
    }

    @Test
    void selects_last_valid_final_answer_after_earlier_incomplete_answers(CapturedOutput output) throws Exception {
        String finalJson = "{\"scene\":\"hall\"}";
        Path executable = appServerScript("""
                #!/usr/bin/env bash
                while IFS= read -r line; do
                  case "$line" in
                    *'"method":"initialize"'*) echo '{"id":1,"result":{}}';;
                    *'"method":"thread/start"'*) echo '{"id":2,"result":{"thread":{"id":"thread-retry"}}}';;
                    *'"method":"turn/start"'*)
                      echo '{"id":3,"result":{"turn":{"id":"turn-retry"}}}';
                      echo '{"method":"item/agentMessage/delta","params":{"threadId":"thread-retry","turnId":"turn-retry","itemId":"message-1","delta":"{\\"scene\\":"}}';
                      echo '{"method":"item/completed","params":{"threadId":"thread-retry","turnId":"turn-retry","completedAtMs":1,"item":{"type":"agentMessage","id":"message-1","text":"{\\"scene\\":","phase":"final_answer"}}}';
                      echo '{"method":"item/agentMessage/delta","params":{"threadId":"thread-retry","turnId":"turn-retry","itemId":"message-2","delta":"{\\"scene\\":"}}';
                      echo '{"method":"item/completed","params":{"threadId":"thread-retry","turnId":"turn-retry","completedAtMs":2,"item":{"type":"agentMessage","id":"message-2","text":"{\\"scene\\":","phase":"final_answer"}}}';
                      echo '{"method":"item/agentMessage/delta","params":{"threadId":"thread-retry","turnId":"turn-retry","itemId":"message-3","delta":"{\\"scene\\":"}}';
                      echo '{"method":"item/completed","params":{"threadId":"thread-retry","turnId":"turn-retry","completedAtMs":3,"item":{"type":"agentMessage","id":"message-3","text":"{\\"scene\\":","phase":"final_answer"}}}';
                      echo '{"method":"item/agentMessage/delta","params":{"threadId":"thread-retry","turnId":"turn-retry","itemId":"message-4","delta":"{\\"scene\\":"}}';
                      echo '{"method":"item/completed","params":{"threadId":"thread-retry","turnId":"turn-retry","completedAtMs":4,"item":{"type":"agentMessage","id":"message-4","text":"{\\"scene\\":","phase":"final_answer"}}}';
                      echo '{"method":"item/agentMessage/delta","params":{"threadId":"thread-retry","turnId":"turn-retry","itemId":"message-5","delta":"{\\"scene\\":"}}';
                      echo '{"method":"item/completed","params":{"threadId":"thread-retry","turnId":"turn-retry","completedAtMs":5,"item":{"type":"agentMessage","id":"message-5","text":"{\\"scene\\":","phase":"final_answer"}}}';
                      echo '{"method":"item/agentMessage/delta","params":{"threadId":"thread-retry","turnId":"turn-retry","itemId":"message-6","delta":"{\\"scene\\":"}}';
                      echo '{"method":"item/completed","params":{"threadId":"thread-retry","turnId":"turn-retry","completedAtMs":6,"item":{"type":"agentMessage","id":"message-6","text":"{\\"scene\\":","phase":"final_answer"}}}';
                      echo '{"method":"item/agentMessage/delta","params":{"threadId":"thread-retry","turnId":"turn-retry","itemId":"message-7","delta":"{\\"scene\\":\\"hall\\"}"}}';
                      echo '{"method":"item/completed","params":{"threadId":"thread-retry","turnId":"turn-retry","completedAtMs":7,"item":{"type":"agentMessage","id":"message-7","text":"{\\"scene\\":\\"hall\\"}","phase":"final_answer"}}}';
                      echo '{"method":"turn/completed","params":{"turn":{"id":"turn-retry","status":"completed"}}}';;
                  esac
                done
                """);
        CodexAppServerClient client = CodexAppServerClient.shared(
                executable.toString(), executable.getParent(), Duration.ofSeconds(3), new ObjectMapper());
        try {
            assertThat(client.complete("final-answer-retry", "private prompt", "gpt-5.6-luna")).isEqualTo(finalJson);
            if ("true".equalsIgnoreCase(System.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED"))) {
                assertThat(output).contains("messageCount=7").contains("itemId=message-7")
                        .contains("selectionStrategy=latest_final_answer_item").contains("jsonObject=true")
                        .doesNotContain("private prompt");
            }
        } finally { client.close(); }
    }

    @Test
    void uses_latest_completed_agent_message_when_phase_is_absent() throws Exception {
        Path executable = appServerScript("""
                #!/usr/bin/env bash
                while IFS= read -r line; do
                  case "$line" in
                    *'"method":"initialize"'*) echo '{"id":1,"result":{}}';;
                    *'"method":"thread/start"'*) echo '{"id":2,"result":{"thread":{"id":"thread-legacy"}}}';;
                    *'"method":"turn/start"'*)
                      echo '{"id":3,"result":{"turn":{"id":"turn-legacy"}}}';
                      echo '{"method":"item/agentMessage/delta","params":{"threadId":"thread-legacy","turnId":"turn-legacy","itemId":"legacy-1","delta":"{\\"scene\\":\\"first\\"}"}}';
                      echo '{"method":"item/completed","params":{"threadId":"thread-legacy","turnId":"turn-legacy","completedAtMs":1,"item":{"type":"agentMessage","id":"legacy-1","text":"{\\"scene\\":\\"first\\"}"}}}';
                      echo '{"method":"item/agentMessage/delta","params":{"threadId":"thread-legacy","turnId":"turn-legacy","itemId":"legacy-2","delta":"{\\"scene\\":\\"last\\"}"}}';
                      echo '{"method":"item/completed","params":{"threadId":"thread-legacy","turnId":"turn-legacy","completedAtMs":2,"item":{"type":"agentMessage","id":"legacy-2","text":"{\\"scene\\":\\"last\\"}"}}}';
                      echo '{"method":"turn/completed","params":{"turn":{"id":"turn-legacy","status":"completed"}}}';;
                  esac
                done
                """);
        CodexAppServerClient client = CodexAppServerClient.shared(
                executable.toString(), executable.getParent(), Duration.ofSeconds(3), new ObjectMapper());
        try {
            assertThat(client.complete("legacy-message", "prompt", "gpt-5.6-luna")).isEqualTo("{\"scene\":\"last\"}");
        } finally { client.close(); }
    }

    @Test
    void rejectsMissingDelegationTamperedResultsAndRepeatedDelegation() throws Exception {
        Path executable = appServerScript("#!/usr/bin/env bash\nexit 0\n");
        ObjectMapper mapper = new ObjectMapper();
        JsonNode childSchema = mapper.readTree("{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"value\"],\"properties\":{\"value\":{\"type\":\"string\"}}}");
        JsonNode parentSchema = mapper.readTree("{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"stages\"],\"properties\":{\"stages\":{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"one\"],\"properties\":{\"one\":{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"value\"],\"properties\":{\"value\":{\"type\":\"string\"}}}}}}}");
        var task = new AiExecutionRequest.Subtask("one", "task", childSchema);
        JsonNode childResult = mapper.readTree("{\"one\":{\"value\":\"original\"}}");
        CodexAppServerClient client = CodexAppServerClient.shared(executable.toString(), executable.getParent(), Duration.ofSeconds(1), mapper);
        try {
            assertThatThrownBy(() -> client.validateDelegatedParent("{\"stages\":{\"one\":{\"value\":\"original\"}}}",
                    parentSchema, java.util.List.of(task), childResult, false, false)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> client.validateDelegatedParent("{\"stages\":{\"one\":{\"value\":\"edited\"}}}",
                    parentSchema, java.util.List.of(task), childResult, true, false)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> client.validateDelegatedParent("{\"stages\":{\"one\":{\"value\":\"original\"}}}",
                    parentSchema, java.util.List.of(task), childResult, true, true)).isInstanceOf(IllegalArgumentException.class);
        } finally { client.close(); }
    }

    @Test
    void cancelsSlowSiblingProcessAsSoonAsAnotherChildFailsSchemaValidation() throws Exception {
        Path directory = Files.createTempDirectory("codex-stage-cancel-test");
        Path slowPid = directory.resolve("slow.pid");
        Path executable = directory.resolve("codex");
        Files.writeString(executable, """
                #!/usr/bin/env bash
                delegated=0
                while IFS= read -r line; do
                  case "$line" in
                    *'"method":"initialize"'*) echo '{"id":1,"result":{}}';;
                    *'"method":"thread/start"'*)
                      case "$line" in *'"dynamicTools"'*) delegated=1;; esac
                      echo '{"id":2,"result":{"thread":{"id":"thread"}}}';;
                    *'"method":"turn/start"'*)
                      echo '{"id":3,"result":{"turn":{"id":"turn"}}}';
                      if [ "$delegated" = 1 ]; then
                        echo '{"id":44,"method":"item/tool/call","params":{"callId":"call","tool":"delegate_stage_tasks","arguments":{"stageIds":["fail","slow"]},"threadId":"thread","turnId":"turn"}}';
                        echo '{"method":"item/agentMessage/delta","params":{"delta":"{\\\"stages\\\":{\\\"fail\\\":{\\\"value\\\":\\\"bad\\\"},\\\"slow\\\":{\\\"value\\\":\\\"slow\\\"}}}"}}';
                      else
                        case "$line" in
                          *child-fail*) echo '{"method":"item/agentMessage/delta","params":{"delta":"{}"}}';;
                          *child-slow*) echo "$BASHPID" > SLOW_PID_PATH; continue;;
                        esac
                      fi
                      echo '{"method":"turn/completed","params":{"turn":{"id":"turn","status":"completed"}}}';;
                  esac
                done
                """.replace("SLOW_PID_PATH", slowPid.toString()));
        assertThat(executable.toFile().setExecutable(true)).isTrue();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode childSchema = mapper.readTree("{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"value\"],\"properties\":{\"value\":{\"type\":\"string\"}}}");
        JsonNode parentSchema = mapper.readTree("{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"stages\"],\"properties\":{\"stages\":{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"fail\",\"slow\"],\"properties\":{\"fail\":{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"value\"],\"properties\":{\"value\":{\"type\":\"string\"}}},\"slow\":{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"value\"],\"properties\":{\"value\":{\"type\":\"string\"}}}}}}}");
        var tasks = java.util.List.of(new AiExecutionRequest.Subtask("fail", "child-fail", childSchema),
                new AiExecutionRequest.Subtask("slow", "child-slow", childSchema));
        CodexAppServerClient client = CodexAppServerClient.shared(executable.toString(), directory, Duration.ofSeconds(5), mapper);
        long started = System.nanoTime();
        try {
            assertThatThrownBy(() -> client.completeWithUsage("batch", "parent", "model", "medium", parentSchema, "", tasks))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("stage delegation failed");
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThan(3000L);
            assertThat(Files.exists(slowPid)).isTrue();
            long pid = Long.parseLong(Files.readString(slowPid).trim());
            assertThat(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)).isFalse();
        } finally { client.close(); }
    }

    @Test
    void delegatesEachFixedStageThroughItsOwnOutputSchemaAndChecksParentDidNotChangeResults() throws Exception {
        Path directory = Files.createTempDirectory("codex-stage-delegation-test");
        Path calls = directory.resolve("calls.jsonl");
        Path executable = directory.resolve("codex");
        Files.writeString(executable, """
                #!/usr/bin/env bash
                delegated=0
                while IFS= read -r line; do
                  echo "$line" >> CALLS_PATH
                  case "$line" in
                    *'"method":"initialize"'*) echo '{"id":1,"result":{}}';;
                    *'"method":"thread/start"'*)
                      case "$line" in *'"dynamicTools"'*) delegated=1;; esac
                      echo '{"id":2,"result":{"thread":{"id":"thread-test"}}}';;
                    *'"method":"turn/start"'*)
                      echo '{"id":3,"result":{"turn":{"id":"turn-test"}}}';
                      if [ "$delegated" = 1 ]; then
                        echo '{"id":44,"method":"item/tool/call","params":{"callId":"call-1","tool":"delegate_stage_tasks","arguments":{"stageIds":["one","two"]},"threadId":"thread-test","turnId":"turn-test"}}';
                        echo '{"method":"item/agentMessage/delta","params":{"delta":"{\\\"stages\\\":{\\\"one\\\":{\\\"value\\\":\\\"one\\\"},\\\"two\\\":{\\\"value\\\":\\\"two\\\"}}}"}}';
                      else
                        case "$line" in
                          *child-one*) echo '{"method":"item/agentMessage/delta","params":{"delta":"{\\\"value\\\":\\\"one\\\"}"}}';;
                          *child-two*) echo '{"method":"item/agentMessage/delta","params":{"delta":"{\\\"value\\\":\\\"two\\\"}"}}';;
                        esac
                      fi
                      echo '{"method":"turn/completed","params":{"turn":{"id":"turn-test","status":"completed"}}}';;
                  esac
                done
                """.replace("CALLS_PATH", calls.toString()));
        assertThat(executable.toFile().setExecutable(true)).isTrue();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode childSchema = mapper.readTree("""
                {"type":"object","additionalProperties":false,"required":["value"],"properties":{"value":{"type":"string"}}}
                """);
        JsonNode parentSchema = mapper.readTree("""
                {"type":"object","additionalProperties":false,"required":["stages"],"properties":{"stages":{"type":"object","additionalProperties":false,"required":["one","two"],"properties":{"one":{"$ref":"#/definitions/stage"},"two":{"$ref":"#/definitions/stage"}}}},"definitions":{"stage":{"type":"object","additionalProperties":false,"required":["value"],"properties":{"value":{"type":"string"}}}}}
                """);
        var tasks = java.util.List.of(new AiExecutionRequest.Subtask("one", "child-one", childSchema),
                new AiExecutionRequest.Subtask("two", "child-two", childSchema));
        CodexAppServerClient client = CodexAppServerClient.shared(executable.toString(), directory,
                Duration.ofSeconds(5), mapper);
        try {
            assertThat(client.completeWithUsage("batch", "parent", "model", "medium", parentSchema, "", tasks).finalText())
                    .contains("\"stages\"");
            assertThat(Files.readString(calls)).contains("dynamicTools", "outputSchema", "child-one", "child-two", "one", "two");
        } finally { client.close(); }
    }

    @Test
    void sendsUltraEffortForSubagentOrchestration() throws Exception {
        Path directory = Files.createTempDirectory("codex-app-server-ultra-test");
        Path marker = directory.resolve("ultra");
        Path executable = directory.resolve("codex");
        Files.writeString(executable, """
                #!/usr/bin/env bash
                while IFS= read -r line; do
                  case "$line" in
                    *'"method":"initialize"'*) echo '{"id":1,"result":{}}';;
                    *'"method":"thread/start"'*) echo '{"id":2,"result":{"thread":{"id":"thread-ultra"}}}';;
                    *'"method":"turn/start"'*)
                      case "$line" in *'"effort":"ultra"'*) touch MARKER_PATH;; esac
                      echo '{"id":3,"result":{"turn":{"id":"turn-ultra"}}}';
                      echo '{"method":"item/started","params":{"threadId":"thread-ultra","turnId":"turn-ultra","startedAtMs":1,"item":{"type":"collabAgentToolCall","id":"agent-call-1","tool":"spawnAgent","receiverThreadIds":["child-thread-1"],"senderThreadId":"thread-ultra","status":"inProgress","agentsStates":{}}}}';
                      echo '{"method":"item/completed","params":{"threadId":"thread-ultra","turnId":"turn-ultra","completedAtMs":2,"item":{"type":"collabAgentToolCall","id":"agent-call-1","tool":"spawnAgent","receiverThreadIds":["child-thread-1"],"senderThreadId":"thread-ultra","status":"completed","agentsStates":{"child-thread-1":{"status":"completed"}}}}}';
                      echo '{"method":"item/agentMessage/delta","params":{"delta":"ok"}}';
                      echo '{"method":"turn/completed","params":{"turn":{"id":"turn-ultra","status":"completed"}}}';;
                  esac
                done
                """.replace("MARKER_PATH", marker.toString()));
        assertThat(executable.toFile().setExecutable(true)).isTrue();
        CodexAppServerClient client = CodexAppServerClient.shared(
                executable.toString(), executable.getParent(), Duration.ofSeconds(2), new ObjectMapper());
        try {
            assertThat(client.complete("stage-batch", "prompt", "gpt-5.6-luna", "ultra")).isEqualTo("ok");
            assertThat(Files.exists(marker)).isTrue();
        } finally { client.close(); }
    }

    @Test
    void checksExecutableAvailabilityWithoutStartingAProcess() throws Exception {
        Path executable = appServerScript("#!/usr/bin/env bash\ntouch started\nexit 0\n");
        CodexAppServerClient available = CodexAppServerClient.shared(
                executable.toString(), executable.getParent(), Duration.ofSeconds(2), new ObjectMapper());
        CodexAppServerClient missing = CodexAppServerClient.shared(
                executable.resolveSibling("missing-codex").toString(), executable.getParent(), Duration.ofSeconds(2), new ObjectMapper());
        try {
            assertThat(available.isAvailable()).isTrue();
            assertThat(missing.isAvailable()).isFalse();
            assertThat(Files.exists(executable.getParent().resolve("started"))).isFalse();
        } finally {
            available.close();
            missing.close();
        }
    }

    @Test
    void reusesExistingAccountAndExplicitLoginStartsEvenWhenAlreadyAuthenticated() throws Exception {
        Path executable = appServerScript("""
                #!/usr/bin/env bash
                while IFS= read -r line; do
                  case "$line" in
                    *'"method":"initialize"'*) echo '{"id":1,"result":{}}';;
                    *'"method":"account/read"'*) echo '{"id":2,"result":{"account":{"type":"chatgpt"}}}';;
                    *'"method":"account/login/start"'*) echo '{"id":3,"result":{"authUrl":"https://auth.example/approve"}}';;
                  esac
                done
                """);
        CodexAppServerClient client = CodexAppServerClient.shared(
                executable.toString(), executable.getParent(), Duration.ofSeconds(2), new ObjectMapper());
        try {
            assertThat(client.isAuthenticated()).isTrue();
            assertThat(client.startBrowserLogin()).isEqualTo("https://auth.example/approve");
        } finally {
            client.close();
        }
    }

    @Test
    void neverIncludesRawAppServerErrorDetailsInAccountFailures() throws Exception {
        Path executable = appServerScript("""
                #!/usr/bin/env bash
                while IFS= read -r line; do
                  case "$line" in
                    *'"method":"initialize"'*) echo '{"id":1,"result":{}}';;
                    *'"method":"account/read"'*) echo '{"id":2,"error":{"code":401,"message":"PRIVATE_TOKEN_VALUE"}}';;
                  esac
                done
                """);
        CodexAppServerClient client = CodexAppServerClient.shared(
                executable.toString(), executable.getParent(), Duration.ofSeconds(2), new ObjectMapper());
        try {
            assertThatThrownBy(client::isAuthenticated)
                    .hasMessageNotContaining("PRIVATE_TOKEN_VALUE");
        } finally {
            client.close();
        }
    }

    @Test
    void capturesReportedUsageForThisTurnAndKeepsAbsentCountsUnknown() throws Exception {
        Path executable = appServerScript("""
                #!/usr/bin/env bash
                while IFS= read -r line; do
                  case "$line" in
                    *'"method":"initialize"'*) echo '{"id":1,"result":{}}';;
                    *'"method":"thread/start"'*) echo '{"id":2,"result":{"thread":{"id":"thread-usage"}}}';;
                    *'"method":"turn/start"'*)
                      echo '{"id":3,"result":{"turn":{"id":"turn-usage"}}}';
                      echo '{"method":"thread/tokenUsage/updated","params":{"threadId":"other","turnId":"turn-usage","tokenUsage":{"total":{"inputTokens":999,"cachedInputTokens":999,"outputTokens":999}}}}';
                      echo '{"method":"thread/tokenUsage/updated","params":{"threadId":"thread-usage","turnId":"turn-usage","tokenUsage":{"total":{"inputTokens":120,"cachedInputTokens":0}}}}';
                      echo '{"method":"item/agentMessage/delta","params":{"delta":"final"}}';
                      echo '{"method":"turn/completed","params":{"turn":{"id":"turn-usage","status":"completed"}}}';;
                  esac
                done
                """);
        CodexAppServerClient client = CodexAppServerClient.shared(
                executable.toString(), executable.getParent(), Duration.ofSeconds(2), new ObjectMapper());
        try {
            var result = client.completeWithUsage("usage", "prompt", "model", "medium", null, "");
            assertThat(result.finalText()).isEqualTo("final");
            assertThat(result.usage().inputTokens()).isEqualTo(120L);
            assertThat(result.usage().cachedInputTokens()).isZero();
            assertThat(result.usage().outputTokens()).isNull();
        } finally { client.close(); }
    }
    @Test
    void classifiesScenarioCompilationResolutionCallsSeparatelyFromRuntimeCompletion() {
        assertThat(CodexAppServerClient.stage("scenario-compilation:abc:resolution-candidates"))
                .isEqualTo("resolution-candidate-extraction");
        assertThat(CodexAppServerClient.stage("scenario-compilation:abc:resolution-candidate-repair"))
                .isEqualTo("resolution-candidate-repair");
        assertThat(CodexAppServerClient.stage("scenario-runtime-123"))
                .isEqualTo("scenario-runtime-completion");
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
                        assertThat(timeout.phase()).isEqualTo("scenario-runtime-completion");
                        assertThat(timeout.timeoutMillis()).isEqualTo(150L);
                        assertThat(timeout.lastEvent()).isEqualTo("request");
                    });
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
                    .isInstanceOf(CodexAuthenticationRejectedException.class)
                    .hasMessage("Codex app-server rejected authentication");
        } finally {
            client.close();
        }
    }

    @Test
    void sendsBestEffortCancelAndDoesNotWaitForCancelAcknowledgement(CapturedOutput output) throws Exception {
        Path executable = appServerScript("""
                #!/usr/bin/env bash
                while IFS= read -r line; do
                  case "$line" in
                    *'\"method\":\"initialize\"'*) echo '{\"id\":1,\"result\":{}}';;
                    *'\"method\":\"thread/start\"'*) echo '{\"id\":2,\"result\":{\"thread\":{\"id\":\"thread-1\"}}}';;
                    *'\"method\":\"turn/start\"'*) echo '{\"id\":3,\"result\":{\"turn\":{\"id\":\"turn-1\"}}}';;
                    *'\"method\":\"turn/cancel\"'*) sleep 5;;
                  esac
                done
                """);
        CodexAppServerClient client = CodexAppServerClient.shared(
                executable.toString(), executable.getParent(), Duration.ofMillis(300), new ObjectMapper());
        try {
            assertThatThrownBy(() -> client.complete("cancel-timeout", "complete", "gpt-5.6-luna"))
                    .isInstanceOf(CodexTurnTimeoutException.class);
            assertThat(output).contains("ai_agent_turn_cancel_sent provider=codex operationId=cancel-timeout");
        } finally {
            client.close();
        }
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void distinguishesTurnFailedFromDeadlineTimeout(CapturedOutput output) throws Exception {
        Path executable = appServerScript("""
                #!/usr/bin/env bash
                while IFS= read -r line; do
                  case "$line" in
                    *'\"method\":\"initialize\"'*) echo '{\"id\":1,\"result\":{}}';;
                    *'\"method\":\"thread/start\"'*) echo '{\"id\":2,\"result\":{\"thread\":{\"id\":\"thread-1\"}}}';;
                    *'\"method\":\"turn/start\"'*) echo '{\"id\":3,\"result\":{}}'; echo '{\"method\":\"turn/failed\",\"params\":{\"error\":{\"code\":\"unclassified\",\"message\":\"Invalid schema while processing private prompt content\",\"codexErrorInfo\":{\"kind\":\"schema_rejection\"},\"additionalDetails\":{\"property\":\"근거키\"},\"misalignment\":{\"expected\":\"object\"}}}}';;
                  esac
                done
                """);
        CodexAppServerClient client = CodexAppServerClient.shared(
                executable.toString(), executable.getParent(), Duration.ofSeconds(2), new ObjectMapper());
        try {
            assertThatThrownBy(() -> client.complete("turn-failed", "complete", "gpt-5.6-luna"))
                    .isInstanceOfSatisfying(CodexTurnFailedException.class, failure -> {
                        assertThat(failure).hasMessage("Codex app-server execution failed");
                        assertThat(failure.errorCode()).isEqualTo("unclassified");
                        assertThat(failure.errorCategory()).isEqualTo("SCHEMA_REJECTED");
                        assertThat(failure.getMessage()).doesNotContain("private prompt content");
                    });
            if ("true".equalsIgnoreCase(System.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED"))) {
                assertThat(output).contains("dev_codex_turn_failure expectedOutcome=completed actualOutcome=failed")
                        .contains("errorCode=unclassified").contains("messageClass=SCHEMA_REJECTED")
                        .contains("codexErrorInfoFields=[kind]").contains("additionalDetailsFields=[property]")
                        .contains("misalignmentFields=[expected]").contains("additionalDetails.property=근거키")
                        .doesNotContain("private prompt content");
            } else {
                assertThat(output).doesNotContain("dev_codex_turn_failure");
            }
        } finally {
            client.close();
        }
    }

    @Test
    void categorizesCodexOutputLimitWithoutRetainingProviderMessage(CapturedOutput output) throws Exception {
        Path executable = appServerScript("""
                #!/usr/bin/env bash
                while IFS= read -r line; do
                  case "$line" in
                    *'\"method\":\"initialize\"'*) echo '{\"id\":1,\"result\":{}}';;
                    *'\"method\":\"thread/start\"'*) echo '{\"id\":2,\"result\":{\"thread\":{\"id\":\"thread-1\"}}}';;
                    *'\"method\":\"turn/start\"'*) echo '{\"id\":3,\"result\":{\"turn\":{\"id\":\"turn-1\"}}}'; echo '{\"method\":\"thread/tokenUsage/updated\",\"params\":{\"threadId\":\"thread-1\",\"turnId\":\"turn-1\",\"tokenUsage\":{\"total\":{\"outputTokens\":790},\"outputTokensDetails\":{\"reasoningTokens\":600}}}}'; echo '{\"method\":\"turn/failed\",\"params\":{\"error\":{\"code\":\"unclassified\",\"message\":\"private provider text max_output_tokens: 1000; maximum 800 output tokens; actual 800 output tokens\",\"codexErrorInfo\":{\"kind\":\"output_limit_exceeded\"},\"additionalDetails\":{\"max_output_tokens\":1000,\"allowed_output_tokens\":800,\"actual_output_tokens\":800}}}}}';;
                  esac
                done
                """);
        CodexAppServerClient client = CodexAppServerClient.shared(
                executable.toString(), executable.getParent(), Duration.ofSeconds(2), new ObjectMapper());
        try {
            assertThatThrownBy(() -> client.complete("turn-output-limit", "complete", "gpt-5.6-luna"))
                    .isInstanceOfSatisfying(CodexTurnFailedException.class, failure -> {
                        assertThat(failure.errorCode()).isEqualTo("unclassified");
                        assertThat(failure.errorCategory()).isEqualTo("OUTPUT_LIMIT");
                        assertThat(failure.getMessage()).doesNotContain("max_output_tokens");
                    });
            if ("true".equalsIgnoreCase(System.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED"))) {
                assertThat(output).contains("dev_codex_turn_failure expectedOutcome=completed actualOutcome=failed")
                        .contains("messageClass=OUTPUT_LIMIT")
                        .contains("outputLimitKind=MAX_OUTPUT_TOKENS")
                        .contains("requestedOutputTokenLimit=1000")
                        .contains("allowedOutputTokenLimit=800")
                        .contains("actualOutputTokens=800")
                        .contains("usageOutputTokens=790")
                        .contains("usageReasoningTokens=600")
                        .doesNotContain("private provider text");
            } else {
                assertThat(output).doesNotContain("dev_codex_turn_failure");
            }
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
