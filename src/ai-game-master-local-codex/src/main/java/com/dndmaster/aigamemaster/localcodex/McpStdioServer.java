package com.dndmaster.aigamemaster.localcodex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Minimal MCP stdio endpoint exposing the read-only search_rules tool to Codex CLI. */
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
                    result = JSON.readTree("{\"tools\":[{\"name\":\"search_rules\",\"description\":\"행동을 해결할 때 규칙 적용이나 규칙 근거가 필요하면 현재 모험에 선택된 기본 룰북과 추가 룰북을 행동 및 상황을 설명하는 질의로 검색하세요. 두 자료 모두 서버가 확정한 범위 안에서 검색합니다. 이 도구는 근거만 찾으며 적용 여부는 직접 판단하세요.\",\"inputSchema\":{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":1000}},\"required\":[\"query\"],\"additionalProperties\":false},\"annotations\":{\"readOnlyHint\":true,\"destructiveHint\":false,\"openWorldHint\":false}}]}");
                } else if ("tools/call".equals(method)) {
                    String name = request.path("params").path("name").asText();
                    String query = request.path("params").path("arguments").path("query").asText("").trim();
                    if (!"search_rules".equals(name) || query.isBlank() || query.length() > 1000) {
                        result = JSON.readTree("{\"content\":[{\"type\":\"text\",\"text\":\"Search request is invalid.\"}],\"isError\":true}");
                    } else {
                        relayOut.write(JSON.writeValueAsString(JSON.createObjectNode().put("query", query))); relayOut.newLine(); relayOut.flush();
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
