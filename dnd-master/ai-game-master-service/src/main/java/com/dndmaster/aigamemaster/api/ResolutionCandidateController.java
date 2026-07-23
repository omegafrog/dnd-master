package com.dndmaster.aigamemaster.api;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private static final Pattern DICE = Pattern.compile("\\b(\\d+d\\d+(?:\\s*[+-]\\s*\\d+)?)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DC = Pattern.compile("\\bDC\\s*(\\d+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SKILL = Pattern.compile(
            "\\b(Stealth|Perception|Athletics|Acrobatics|Investigation|Insight|Survival|Arcana|History|Medicine|Nature|Religion|Intimidation|Deception|Persuasion|Performance|Sleight of Hand)\\b",
            Pattern.CASE_INSENSITIVE);
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
                + "Return JSON array only. Schema: [{kind:'SKILL_ABILITY_CHECK'|'SAVING_THROW'|'PASSIVE_THRESHOLD'|'DICE_ROLL',"
                + "abilityOrSkill:string|null,dc:number|null,diceExpression:string|null,visibility:'GM_REFERENCE',"
                + "sourceQuote:string,sourceRefs:[{documentId:string,extractionVersion:number,locator:string}],provenance:string}]."
                + " Do not invent values or references. Excerpts: " + request.excerpts();
        try {
            return new Response(adapter.complete(request.operationId(), prompt,
                    text -> parseModel(text, request.excerpts())));
        } catch (RuntimeException unavailable) {
            return new Response(request.excerpts().stream()
                    .filter(excerpt -> excerpt != null && excerpt.text() != null && !excerpt.text().isBlank())
                    .flatMap(excerpt -> candidates(excerpt).stream()).toList());
        }
    }

    private List<Candidate> parseModel(String text, List<Excerpt> excerpts) {
        try {
            JsonNode root = objectMapper.readTree(text);
            if (!root.isArray()) throw new IllegalArgumentException("AI resolution response must be an array");
            return java.util.stream.StreamSupport.stream(root.spliterator(), false).map(node -> new Candidate(
                    text(node, "kind"), nullableText(node, "abilityOrSkill"), nullableInt(node, "dc"),
                    nullableText(node, "diceExpression"), "GM_REFERENCE", text(node, "sourceQuote"),
                    parseRefs(node.get("sourceRefs")), text(node, "provenance"))).toList();
        } catch (Exception malformed) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI resolution response was malformed", malformed);
        }
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

    private static List<Candidate> candidates(Excerpt excerpt) {
        String text = excerpt.text();
        String provenance = "source-tracer-v1/" + safe(requestVersionPlaceholder());
        Matcher dice = DICE.matcher(text);
        if (dice.find()) {
            return List.of(new Candidate("DICE_ROLL", null, null, dice.group(1), "GM_REFERENCE",
                    text, List.of(new SourceRef(excerpt.documentId(), excerpt.extractionVersion(), excerpt.locator())), provenance));
        }
        Matcher dc = DC.matcher(text);
        Matcher skill = SKILL.matcher(text);
        if (dc.find() && skill.find()) {
            return List.of(new Candidate("SKILL_ABILITY_CHECK", skill.group(1), Integer.valueOf(dc.group(1)), null,
                    "GM_REFERENCE", text, List.of(new SourceRef(excerpt.documentId(), excerpt.extractionVersion(), excerpt.locator())), provenance));
        }
        return List.of();
    }

    private static String requestVersionPlaceholder() { return "schema-v1"; }
    private static String safe(String value) { return value.replaceAll("[^a-zA-Z0-9._-]", "_"); }

    public record Request(String operationId, List<Excerpt> excerpts, String schemaVersion, String promptVersion) {}
    public record Excerpt(UUID documentId, long extractionVersion, String locator, String text) {}
    public record Response(List<Candidate> candidates) {}
    public record Candidate(String kind, String abilityOrSkill, Integer dc, String diceExpression,
                            String visibility, String sourceQuote, List<SourceRef> sourceRefs, String provenance) {}
    public record SourceRef(UUID documentId, long extractionVersion, String locator) {}
}
