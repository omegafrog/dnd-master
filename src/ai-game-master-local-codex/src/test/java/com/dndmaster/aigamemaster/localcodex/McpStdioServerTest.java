package com.dndmaster.aigamemaster.localcodex;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class McpStdioServerTest {
    @Test
    void listsOnlyTheReadOnlyRulesSearchToolUsingJsonRpcOnStdout() throws Exception {
        try (ServerSocket listener = new ServerSocket(0); var executor = Executors.newSingleThreadExecutor()) {
            var receivedToken = executor.submit(() -> {
                try (Socket peer = listener.accept();
                     BufferedReader input = new BufferedReader(new java.io.InputStreamReader(peer.getInputStream(), StandardCharsets.UTF_8))) {
                    return input.readLine();
                }
            });
            try (Socket client = new Socket("127.0.0.1", listener.getLocalPort());
                 var stdIn = new BufferedReader(new StringReader("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}\n"));
                 var stdOutBytes = new ByteArrayOutputStream();
                 var stdOut = new BufferedWriter(new OutputStreamWriter(stdOutBytes, StandardCharsets.UTF_8))) {
                McpStdioServer.serve(client, "run-token", stdIn, stdOut);
                assertThat(receivedToken.get()).isEqualTo("run-token");
                var tool = new ObjectMapper().readTree(stdOutBytes.toString(StandardCharsets.UTF_8))
                        .path("result").path("tools").get(0);
                assertThat(tool.path("name").asText()).isEqualTo("search_rules");
                assertThat(tool.path("annotations").path("readOnlyHint").asBoolean()).isTrue();
                assertThat(tool.path("annotations").path("destructiveHint").asBoolean()).isFalse();
                assertThat(tool.path("annotations").path("openWorldHint").asBoolean()).isFalse();
                assertThat(tool.path("inputSchema").path("additionalProperties").asBoolean()).isFalse();
            }
        }
    }

    @Test
    void returnsSearchResultsAsToolTextAndMarksSearchFailures() throws Exception {
        try (ServerSocket listener = new ServerSocket(0); var executor = Executors.newSingleThreadExecutor()) {
            var relay = executor.submit(() -> {
                try (Socket peer = listener.accept();
                     BufferedReader input = new BufferedReader(new java.io.InputStreamReader(peer.getInputStream(), StandardCharsets.UTF_8));
                     BufferedWriter output = new BufferedWriter(new OutputStreamWriter(peer.getOutputStream(), StandardCharsets.UTF_8))) {
                    input.readLine();
                    assertThat(input.readLine()).contains("perception rules");
                    output.write("{\"success\":false,\"error\":\"search unavailable\"}\n"); output.flush();
                }
                return null;
            });
            try (Socket client = new Socket("127.0.0.1", listener.getLocalPort());
                 var stdIn = new BufferedReader(new StringReader("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"search_rules\",\"arguments\":{\"query\":\"perception rules\"}}}\n"));
                 var stdOutBytes = new ByteArrayOutputStream();
                 var stdOut = new BufferedWriter(new OutputStreamWriter(stdOutBytes, StandardCharsets.UTF_8))) {
                McpStdioServer.serve(client, "run-token", stdIn, stdOut);
                relay.get();
                assertThat(stdOutBytes.toString(StandardCharsets.UTF_8)).contains("search unavailable", "isError");
            }
        }
    }

    @Test
    void learnedAbilityLookupPreservesEachLookupOutcomeAcrossTheLocalRelay() throws Exception {
        var mapper = new ObjectMapper();
        for (String status : java.util.List.of("FOUND", "NEEDS_PREPARATION", "NOT_LEARNED")) {
            var definitionResult = mapper.createObjectNode().put("status", status);
            if ("FOUND".equals(status)) {
                definitionResult.set("definition", mapper.readTree("{\"abilityId\":\"ability-id-actual-42\",\"sourceLocations\":[{\"documentId\":\"doc-1\",\"extractionVersion\":2,\"locator\":\"page=4;node=7\"}]}"));
            } else {
                definitionResult.putNull("definition");
            }
            var content = callAbilityLookup(mapper.createObjectNode().put("success", true).set("result", definitionResult).toString());
            assertThat(content.path("isError").asBoolean(false)).isFalse();
            var result = new ObjectMapper().readTree(content.path("content").get(0).path("text").asText());
            assertThat(result.path("status").asText()).isEqualTo(status);
            if ("FOUND".equals(status)) {
                assertThat(result.path("definition").path("abilityId").asText()).isEqualTo("ability-id-actual-42");
                assertThat(result.path("definition").path("sourceLocations").get(0).path("documentId").asText())
                        .isEqualTo("doc-1");
            } else {
                assertThat(result.path("definition").isNull()).isTrue();
            }
        }

        var failure = callAbilityLookup("{\"success\":false,\"error\":\"ability lookup unavailable\"}");
        assertThat(failure.path("isError").asBoolean()).isTrue();
        assertThat(failure.path("content").get(0).path("text").asText()).contains("ability lookup unavailable");
    }

    private static com.fasterxml.jackson.databind.JsonNode callAbilityLookup(String relayResponse) throws Exception {
        try (ServerSocket listener = new ServerSocket(0); var executor = Executors.newSingleThreadExecutor()) {
            var relay = executor.submit(() -> {
                try (Socket peer = listener.accept();
                     BufferedReader input = new BufferedReader(new java.io.InputStreamReader(peer.getInputStream(), StandardCharsets.UTF_8));
                     BufferedWriter output = new BufferedWriter(new OutputStreamWriter(peer.getOutputStream(), StandardCharsets.UTF_8))) {
                    input.readLine();
                    assertThat(new ObjectMapper().readTree(input.readLine()).path("arguments").path("query").asText())
                            .isEqualTo("ability-id-actual-42");
                    output.write(relayResponse + "\n"); output.flush();
                }
                return null;
            });
            try (Socket client = new Socket("127.0.0.1", listener.getLocalPort());
                 var stdIn = new BufferedReader(new StringReader("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"lookup_learned_ability\",\"arguments\":{\"abilityId\":\"ability-id-actual-42\"}}}\n"));
                 var stdOutBytes = new ByteArrayOutputStream();
                 var stdOut = new BufferedWriter(new OutputStreamWriter(stdOutBytes, StandardCharsets.UTF_8))) {
                McpStdioServer.serve(client, "run-token", stdIn, stdOut);
                relay.get();
                return new ObjectMapper().readTree(stdOutBytes.toString(StandardCharsets.UTF_8)).path("result");
            }
        }
    }
}
