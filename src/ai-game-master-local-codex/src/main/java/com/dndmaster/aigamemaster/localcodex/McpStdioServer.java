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
                    result = JSON.readTree("{\"tools\":[{\"name\":\"search_rules\",\"description\":\"행동을 해결할 때 규칙 적용이나 규칙 근거가 필요하면 현재 모험에 선택된 룰북을 검색하세요.\",\"inputSchema\":{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":1000}},\"required\":[\"query\"],\"additionalProperties\":false},\"annotations\":{\"readOnlyHint\":true,\"destructiveHint\":false,\"openWorldHint\":false}},{\"name\":\"find_scenario_elements\",\"description\":\"시나리오 자료의 인물·장소·목표·사건·관계·단서를 확인하세요. 현재 모험에 잠긴 자료에서 실제 요소 ID와 일치하는 내용만 반환합니다. 상황 근거를 SCENARIO로 정하는 모든 경우 반드시 먼저 호출하고, 반환된 elementId만 reference로 사용하세요. 결과가 없으면 시나리오 ID를 추측하지 마세요.\",\"inputSchema\":{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":1000}},\"required\":[\"query\"],\"additionalProperties\":false},\"annotations\":{\"readOnlyHint\":true,\"destructiveHint\":false,\"openWorldHint\":false}},{\"name\":\"find_available_situations\",\"description\":\"일반 턴 요청 전에 로컬 에이전트가 권한 있는 MCP 경로로 자동 조회합니다. 같은 단계의 선택 가능한 비필수 상황과 선행 조건이 열린 필수 그래프 상황을 반환하며, 각 후보의 stageId·제목·설명·종료 여부를 포함합니다. 비필수 상황은 모험 준비에서 정한 허용량 안에서 행동과 자연스럽게 이어질 때만 선택하고, 그 외에는 적합한 필수 상황을 선택하세요. 전환에는 반환된 situationId만 사용합니다. 시작·종료 상황에서는 조회하지 않습니다.\",\"inputSchema\":{\"type\":\"object\",\"properties\":{},\"required\":[],\"additionalProperties\":false},\"annotations\":{\"readOnlyHint\":true,\"destructiveHint\":false,\"openWorldHint\":false}}]}");
                    ((com.fasterxml.jackson.databind.node.ArrayNode) result.path("tools")).add(JSON.createObjectNode()
                            .put("name", "lookup_learned_ability")
                            .put("description", "현재 모험에서 캐릭터가 실제로 배운 능력의 저장된 상세정보를 능력 ID로 조회합니다. 이름으로 ID를 추정하지 마세요.")
                            .set("inputSchema", JSON.readTree("{\"type\":\"object\",\"properties\":{\"abilityId\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":200}},\"required\":[\"abilityId\"],\"additionalProperties\":false}")));
                } else if ("tools/call".equals(method)) {
                    String name = request.path("params").path("name").asText();
                    String query = request.path("params").path("arguments").path("query").asText("").trim();
                    String abilityId = request.path("params").path("arguments").path("abilityId").asText("").trim();
                    boolean abilityLookup = "lookup_learned_ability".equals(name);
                    boolean situationLookup = "find_available_situations".equals(name);
                    if (!("search_rules".equals(name) || "find_scenario_elements".equals(name) || abilityLookup || situationLookup)
                            || abilityLookup && (abilityId.isBlank() || abilityId.length() > 200)
                            || !abilityLookup && !situationLookup && (query.isBlank() || query.length() > 1000)) {
                        result = JSON.readTree("{\"content\":[{\"type\":\"text\",\"text\":\"Search request is invalid.\"}],\"isError\":true}");
                    } else {
                        var call = JSON.createObjectNode().put("tool", name);
                        // The internal Codex relay carries one text argument; the WebSocket
                        // adapter maps it back to the public tool's abilityId field.
                        call.set("arguments", situationLookup ? JSON.createObjectNode()
                                : JSON.createObjectNode().put("query", abilityLookup ? abilityId : query));
                        relayOut.write(JSON.writeValueAsString(call)); relayOut.newLine(); relayOut.flush();
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
