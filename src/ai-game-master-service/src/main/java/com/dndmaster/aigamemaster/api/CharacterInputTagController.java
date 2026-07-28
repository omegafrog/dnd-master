package com.dndmaster.aigamemaster.api;

import com.dndmaster.aigamemaster.infrastructure.ai.SpringAiChatAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public final class CharacterInputTagController {
    private final SpringAiChatAdapter adapter;
    private final ObjectMapper objectMapper;
    public CharacterInputTagController(SpringAiChatAdapter adapter, ObjectMapper objectMapper) { this.adapter = adapter; this.objectMapper = objectMapper; }

    @PostMapping("/internal/v1/gm/character-input-tags")
    Response extract(@RequestBody Request request) {
        if (request == null || request.excerpts() == null) return new Response(List.of());
        String prompt = "Extract only source-grounded character input tags. Return JSON array only. "
                + "Schema: [{key:string,label:string,parentKey:string|null,required:boolean,inputMode:'FREE_TEXT'|'SINGLE_SELECT'|'MULTI_SELECT',options:string[],suggestions:string[],confidence:'HIGH'|'MEDIUM'|'LOW',sourceQuote:string,evidence:[{documentId:string,extractionVersion:number,locator:string}],sourceType:'RULEBOOK'|'STORYBOOK'}]. "
                + "Do not invent fields, values, or evidence. " + request.excerpts();
        return new Response(adapter.complete(request.operationId(), prompt, this::parseModel));
    }

    List<Candidate> parseModel(String text) {
        try {
            JsonNode root = objectMapper.readTree(extractJsonArray(text));
            if (!root.isArray()) throw new IllegalArgumentException("AI character tag response must be an array");
            List<Candidate> result = new ArrayList<>();
            for (JsonNode node : root) {
                try {
                    String key = required(node, "key");
                    String mode = node.path("inputMode").asText("FREE_TEXT");
                    InputMode.valueOf(mode);
                    List<String> options = strings(node.get("options"));
                    if ("FREE_TEXT".equals(mode) && !options.isEmpty()) throw new IllegalArgumentException("free-text options");
                    String confidence = node.path("confidence").asText("LOW");
                    if (!List.of("HIGH", "MEDIUM", "LOW").contains(confidence)) throw new IllegalArgumentException("unsupported confidence");
                    String sourceType = node.path("sourceType").asText("RULEBOOK");
                    if (!List.of("RULEBOOK", "STORYBOOK").contains(sourceType)) throw new IllegalArgumentException("unsupported source type");
                    List<SourceRef> evidence = refs(node.get("evidence"));
                    String quote = required(node, "sourceQuote");
                    if (evidence.isEmpty()) continue;
                    result.add(new Candidate(key, node.path("label").asText(key), nullable(node, "parentKey"), node.path("required").asBoolean(false), mode, options, strings(node.get("suggestions")), confidence, quote, evidence, sourceType));
                } catch (RuntimeException ignored) { }
            }
            return List.copyOf(result);
        } catch (Exception malformed) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI character tag response was malformed", malformed); }
    }

    private static String extractJsonArray(String response) { String value = response == null ? "" : response.trim(); int start = value.indexOf('['), end = value.lastIndexOf(']'); if (start < 0 || end < start) throw new IllegalArgumentException("array missing"); return value.substring(start, end + 1); }
    private static String required(JsonNode node, String name) { String value = node.path(name).asText(""); if (value.isBlank()) throw new IllegalArgumentException(name + " missing"); return value; }
    private static String nullable(JsonNode node, String name) { JsonNode value = node.get(name); return value == null || value.isNull() ? null : value.asText(); }
    private static List<String> strings(JsonNode node) { if (node == null || !node.isArray()) return List.of(); List<String> result = new ArrayList<>(); node.forEach(value -> { if (value.isTextual() && !value.asText().isBlank()) result.add(value.asText()); }); return List.copyOf(result); }
    private static List<SourceRef> refs(JsonNode node) { if (node == null || !node.isArray()) return List.of(); List<SourceRef> result = new ArrayList<>(); node.forEach(value -> { try { result.add(new SourceRef(UUID.fromString(required(value, "documentId")), value.path("extractionVersion").asLong(), required(value, "locator"))); } catch (RuntimeException ignored) { } }); return List.copyOf(result); }
    public record Request(String operationId, List<Excerpt> excerpts, String schemaVersion, String promptVersion) {}
    public record Excerpt(UUID documentId, long extractionVersion, String locator, String text) {}
    public record Response(List<Candidate> candidates) {}
    public record Candidate(String key, String label, String parentKey, boolean required, String inputMode, List<String> options, List<String> suggestions, String confidence, String sourceQuote, List<SourceRef> evidence, String sourceType) {}
    public record SourceRef(UUID documentId, long extractionVersion, String locator) {}
    private enum InputMode { FREE_TEXT, SINGLE_SELECT, MULTI_SELECT }
}
