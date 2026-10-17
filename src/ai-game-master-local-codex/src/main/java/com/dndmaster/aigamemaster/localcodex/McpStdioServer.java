package com.dndmaster.aigamemaster.localcodex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Minimal MCP stdio endpoint exposing read-only runtime knowledge tools to Codex CLI. */
public final class McpStdioServer {
    private static final ObjectMapper JSON = new ObjectMapper();
    private McpStdioServer() {}
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("host, port and run token are required");
        try (Socket socket = new Socket(args[0], Integer.parseInt(args[1]));
             BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
             BufferedWriter output = new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8))) {
            serve(socket, args[2], input, output);
        }
    }

    static void serve(Socket socket, String token, BufferedReader input, BufferedWriter output) throws Exception {
        try (BufferedReader relayIn = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter relayOut = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            relayOut.write(token); relayOut.newLine(); relayOut.flush();
            String line;
            while ((line = input.readLine()) != null) {
                JsonNode request = JSON.readTree(line);
                JsonNode id = request.get("id");
                String method = request.path("method").asText();
                JsonNode result;
                if ("initialize".equals(method)) {
                    result = JSON.readTree("{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{\"tools\":{}},\"serverInfo\":{\"name\":\"dnd-rag-search\",\"version\":\"1.0\"}}");
                } else if ("notifications/initialized".equals(method)) {
                    continue;
                } else if ("tools/list".equals(method)) {
                    result = JSON.readTree("{\"tools\":[{\"name\":\"search_rules\",\"description\":\"행동을 해결할 때 규칙 적용이나 규칙 근거가 필요하면 현재 모험에 선택된 룰북을 검색하세요.\",\"inputSchema\":{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":1000}},\"required\":[\"query\"],\"additionalProperties\":false},\"annotations\":{\"readOnlyHint\":true,\"destructiveHint\":false,\"openWorldHint\":false}},{\"name\":\"find_scenario_elements\",\"description\":\"시나리오 자료의 인물·장소·목표·사건·관계·단서를 확인하세요. 현재 모험에 잠긴 자료에서 실제 요소 ID와 일치하는 내용만 반환합니다. 상황 근거를 SCENARIO로 정하는 모든 경우 반드시 먼저 호출하고, 반환된 elementId만 reference로 사용하세요. 결과가 없으면 시나리오 ID를 추측하지 마세요.\",\"inputSchema\":{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":1000}},\"required\":[\"query\"],\"additionalProperties\":false},\"annotations\":{\"readOnlyHint\":true,\"destructiveHint\":false,\"openWorldHint\":false}}]}");
                } else if ("tools/call".equals(method)) {
                    String name = request.path("params").path("name").asText();
                    String query = request.path("params").path("arguments").path("query").asText("").trim();
                    if (!("search_rules".equals(name) || "find_scenario_elements".equals(name)) || query.isBlank() || query.length() > 1000) {
                        result = JSON.readTree("{\"content\":[{\"type\":\"text\",\"text\":\"Search request is invalid.\"}],\"isError\":true}");
                    } else {
                        relayOut.write(JSON.writeValueAsString(JSON.createObjectNode().put("tool", name).put("query", query))); relayOut.newLine(); relayOut.flush();
                        JsonNode searched = JSON.readTree(relayIn.readLine());
                        boolean success = searched.path("success").asBoolean(false);
                        String text = success ? JSON.writeValueAsString(searched.path("result")) : searched.path("error").asText("RAG search failed");
                        com.fasterxml.jackson.databind.node.ObjectNode toolResult = JSON.createObjectNode()
                                .set("content", JSON.createArrayNode().add(JSON.createObjectNode()
                                        .put("type", "text").put("text", text)));
                        if (!success) toolResult.put("isError", true);
                        result = toolResult;
                    }
                } else if ("ping".equals(method)) {
                    result = JSON.createObjectNode();
                } else {
                    if (id == null || id.isNull()) continue;
                    writeError(output, id, -32601, "Method not found"); continue;
                }
                if (id == null || id.isNull()) continue;
                var response = JSON.createObjectNode().put("jsonrpc", "2.0"); response.set("id", id); response.set("result", result);
                output.write(JSON.writeValueAsString(response)); output.newLine(); output.flush();
            }
        }
    }
    private static void writeError(BufferedWriter output, JsonNode id, int code, String message) throws Exception {
        var error = JSON.createObjectNode().put("code", code).put("message", message);
        var response = JSON.createObjectNode().put("jsonrpc", "2.0"); response.set("id", id); response.set("error", error);
        output.write(JSON.writeValueAsString(response)); output.newLine(); output.flush();
    }
}
