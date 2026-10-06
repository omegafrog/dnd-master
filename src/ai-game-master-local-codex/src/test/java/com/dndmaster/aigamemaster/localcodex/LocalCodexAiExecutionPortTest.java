package com.dndmaster.aigamemaster.localcodex;

import static org.assertj.core.api.Assertions.assertThat;

import com.dndmaster.aigamemaster.application.ai.AiExecutionRequest;
import com.dndmaster.aigamemaster.application.ai.AiExecutionSuccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalCodexAiExecutionPortTest {
    @TempDir Path tempDir;

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
}
