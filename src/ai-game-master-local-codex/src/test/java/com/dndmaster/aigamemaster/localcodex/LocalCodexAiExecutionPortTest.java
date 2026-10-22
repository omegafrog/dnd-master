package com.dndmaster.aigamemaster.localcodex;

import static org.assertj.core.api.Assertions.assertThat;

import com.dndmaster.aigamemaster.application.ai.AiExecutionRequest;
import com.dndmaster.aigamemaster.application.ai.AiExecutionSuccess;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalCodexAiExecutionPortTest {
    @TempDir Path tempDir;

    @Test
    void relayReadsTheMcpArgumentFromItsNestedArgumentsObject() throws Exception {
        var call = new ObjectMapper().readTree("{\"tool\":\"find_scenario_elements\",\"arguments\":{\"query\":\"cellar stairs\"}}");

        assertThat(LocalCodexAiExecutionPort.queryArgument(call)).isEqualTo("cellar stairs");
    }

    @Test
    void startsAppServerWithTheWebSocketBackedSearchToolAndOmitsNullOutputSchema() throws Exception {
        Path fakeCodex = tempDir.resolve("fake-codex");
        Path capturedArguments = tempDir.resolve("arguments.txt");
        Path capturedRequests = tempDir.resolve("requests.jsonl");
        Files.writeString(fakeCodex, "#!/usr/bin/env bash\n"
                + "printf '%s\\n' \"$@\" > '" + capturedArguments + "'\n"
                + "while IFS= read -r line; do\n"
                + "  printf '%s\\n' \"$line\" >> '" + capturedRequests + "'\n"
                + "  case \"$line\" in\n"
                + "    *'\"method\":\"initialize\"'*) echo '{\"id\":1,\"result\":{}}';;\n"
                + "    *'\"method\":\"thread/start\"'*) echo '{\"id\":2,\"result\":{\"thread\":{\"id\":\"thread-1\"}}}';;\n"
                + "    *'\"method\":\"turn/start\"'*)\n"
                + "      echo '{\"id\":3,\"result\":{\"turn\":{\"id\":\"turn-1\"}}}'\n"
                + "      echo '{\"method\":\"item/agentMessage/delta\",\"params\":{\"delta\":\"OK\"}}'\n"
                + "      echo '{\"method\":\"turn/completed\",\"params\":{\"turn\":{\"id\":\"turn-1\",\"status\":\"completed\"}}}'\n"
                + "      ;;\n"
                + "  esac\n"
                + "done\n");
        assertThat(fakeCodex.toFile().setExecutable(true)).isTrue();

        var port = new LocalCodexAiExecutionPort(fakeCodex.toString(), tempDir,
                Duration.ofSeconds(5), new ObjectMapper());
        try {
            var result = port.execute(new AiExecutionRequest(
                    UUID.randomUUID(), "request-app-server-mcp", "operation", "prompt",
                    "gpt-5.6-luna", "medium", "json", NullNode.getInstance(), "",
                    TextNode.valueOf("authorized-search-context")));

            assertThat(result).isInstanceOf(AiExecutionSuccess.class);
            assertThat(((AiExecutionSuccess) result).finalText()).isEqualTo("OK");
            assertThat(Files.readString(capturedArguments))
                    .contains("app-server", "mcp_servers.rag_search.command=", "mcp_servers.rag_search.args=")
                    .contains("search_rules", "127.0.0.1");
            String turnRequest = Files.readAllLines(capturedRequests).stream()
                    .filter(line -> line.contains("\"method\":\"turn/start\""))
                    .findFirst().orElseThrow();
            assertThat(turnRequest).doesNotContain("outputSchema");
        } finally {
            port.close();
        }
    }

    @Test
    void fetchesAvailableSituationsThroughMcpBeforeStartingAnOrdinaryRuntimeTurn() throws Exception {
        Path fakeCodex = tempDir.resolve("fake-runtime-codex");
        Path capturedRequests = tempDir.resolve("runtime-requests.jsonl");
        Files.writeString(fakeCodex, "#!/usr/bin/env bash\n"
                + "while IFS= read -r line; do\n"
                + "  printf '%s\\n' \"$line\" >> '" + capturedRequests + "'\n"
                + "  case \"$line\" in\n"
                + "    *'\"method\":\"initialize\"'*) echo '{\"id\":1,\"result\":{}}';;\n"
                + "    *'\"method\":\"thread/start\"'*) echo '{\"id\":2,\"result\":{\"thread\":{\"id\":\"thread-1\"}}}';;\n"
                + "    *'\"method\":\"turn/start\"'*)\n"
                + "      echo '{\"id\":3,\"result\":{\"turn\":{\"id\":\"turn-1\"}}}'\n"
                + "      echo '{\"method\":\"item/agentMessage/delta\",\"params\":{\"delta\":\"OK\"}}'\n"
                + "      echo '{\"method\":\"turn/completed\",\"params\":{\"turn\":{\"id\":\"turn-1\",\"status\":\"completed\"}}}'\n"
                + "      ;;\n"
                + "  esac\n"
                + "done\n");
        assertThat(fakeCodex.toFile().setExecutable(true)).isTrue();

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode schema = mapper.createObjectNode().put("type", "object");
        schema.putObject("properties").putObject("situation").put("type", "object");
        AtomicInteger lookupCalls = new AtomicInteger();
        var port = new LocalCodexAiExecutionPort(fakeCodex.toString(), tempDir, Duration.ofSeconds(5), mapper);
        port.setMcpToolHandler((requestId, tool, query) -> {
            assertThat(tool).isEqualTo("find_available_situations");
            assertThat(query).isEmpty();
            lookupCalls.incrementAndGet();
            return mapper.readTree("{\"success\":true,\"result\":{\"situations\":[{\"situationId\":\"stable-situation-id\"}]}}");
        });
        try {
            var result = port.execute(new AiExecutionRequest(UUID.randomUUID(), "runtime-turn-1", "operation",
                    "runtime prompt", "gpt-6-luna", "low", "json", schema, "",
                    TextNode.valueOf("authorized-search-context")));

            assertThat(result).isInstanceOf(AiExecutionSuccess.class);
            assertThat(lookupCalls).hasValue(1);
            String turnStart = Files.readAllLines(capturedRequests).stream()
                    .filter(line -> line.contains("\"method\":\"turn/start\""))
                    .findFirst().orElseThrow();
            JsonNode prompt = mapper.readTree(turnStart).path("params").path("input").get(0).path("text");
            assertThat(prompt.asText()).contains("AVAILABLE_SITUATIONS_MCP_RESULT=")
                    .contains("stable-situation-id");
        } finally {
            port.close();
        }
    }
}
