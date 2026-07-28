package com.dndmaster.aigamemaster.api;

import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import com.dndmaster.aigamemaster.infrastructure.ai.SpringAiChatAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Source-grounded fallback tracer for the supported simple resolution grammar. */
@RestController
public final class ResolutionCandidateController {
    private final SpringAiChatAdapter adapter;
    private final ObjectMapper objectMapper;

    public ResolutionCandidateController(SpringAiChatAdapter adapter, ObjectMapper objectMapper) {
        this.adapter = adapter;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/internal/v1/gm/resolution-candidates")
    Response extract(@RequestBody Request request) {
        if (request == null || request.excerpts() == null) return new Response(List.of());
        String prompt = "Extract only directly supported tabletop resolution candidates from these source excerpts. "
                + "Return JSON array only. Schema: [{kind:'SKILL_ABILITY_CHECK'|'SAVING_THROW'|'PASSIVE_THRESHOLD'|'DICE_ROLL'|'ATTACK_ROLL'|'DAMAGE_ROLL'|'HEALING_ROLL'|'OPPOSED_CHECK'|'INITIATIVE_ROLL'|'RECHARGE_ROLL'|'RANDOM_TABLE'|'SPECIAL_ROLL',"
                + "abilityOrSkill:string|null,dc:number|null,diceExpression:string|null,visibility:'GM_REFERENCE',"
                + "sourceQuote:string,sourceRefs:[{documentId:string,extractionVersion:number,locator:string}],"
                + "detail:null,"
                + "provenance:string}]. Output raw JSON only: no Markdown fences, commentary, or leading/trailing text."
                + " Do not invent values or references. Excerpts: " + request.excerpts();
        return new Response(adapter.complete(request.operationId(), prompt, this::parseModel));
    }

    List<Candidate> parseModel(String text) {
        try {
            JsonNode root = objectMapper.readTree(extractJsonArray(text));
            if (!root.isArray()) throw new IllegalArgumentException("AI resolution response must be an array");
            List<Candidate> candidates = new ArrayList<>();
            for (JsonNode node : root) {
                try {
                    String kind = text(node, "kind");
                    if (!List.of("SKILL_ABILITY_CHECK", "SAVING_THROW", "PASSIVE_THRESHOLD", "DICE_ROLL",
                            "ATTACK_ROLL", "DAMAGE_ROLL", "HEALING_ROLL", "OPPOSED_CHECK",
                            "INITIATIVE_ROLL", "RECHARGE_ROLL", "RANDOM_TABLE", "SPECIAL_ROLL").contains(kind)) {
                        throw new IllegalArgumentException("unsupported resolution kind");
                    }
                    String visibility = nullableText(node, "visibility");
                    if (visibility == null) visibility = "GM_REFERENCE";
                    if (!List.of("GM_REFERENCE", "PLAYER_SAFE").contains(visibility)) {
                        throw new IllegalArgumentException("unsupported visibility");
                    }
                    candidates.add(new Candidate(kind, nullableText(node, "abilityOrSkill"), nullableInt(node, "dc"),
                            nullableText(node, "diceExpression"), visibility, text(node, "sourceQuote"),
                            parseRefs(node.get("sourceRefs")), node.get("detail"), text(node, "provenance")));
                } catch (RuntimeException ignored) {
                    // One malformed model item must not discard valid source-grounded candidates.
                }
            }
            return List.copyOf(candidates);
        } catch (Exception malformed) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI resolution response was malformed", malformed);
        }
    }

    private static String extractJsonArray(String response) {
        String text = response == null ? "" : response.trim();
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start < 0 || end < start) throw new IllegalArgumentException("AI response did not contain a JSON array");
        return text.substring(start, end + 1);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) throw new IllegalArgumentException(field + " missing");
        return value.textValue();
    }
    private static String nullableText(JsonNode node, String field) {
        JsonNode value = node.get(field); return value == null || value.isNull() ? null : value.asText();
    }
    private static Integer nullableInt(JsonNode node, String field) {
        JsonNode value = node.get(field); return value == null || value.isNull() ? null : value.asInt();
    }
    private static List<SourceRef> parseRefs(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        return java.util.stream.StreamSupport.stream(node.spliterator(), false).map(ref ->
                new SourceRef(UUID.fromString(text(ref, "documentId")), ref.get("extractionVersion").asLong(), text(ref, "locator"))).toList();
    }

    public record Request(String operationId, List<Excerpt> excerpts, String schemaVersion, String promptVersion) {}
    public record Excerpt(UUID documentId, long extractionVersion, String locator, String text) {}
    public record Response(List<Candidate> candidates) {}
    public record Candidate(String kind, String abilityOrSkill, Integer dc, String diceExpression,
                            String visibility, String sourceQuote, List<SourceRef> sourceRefs, JsonNode detail, String provenance) {}
    public record SourceRef(UUID documentId, long extractionVersion, String locator) {}
}
