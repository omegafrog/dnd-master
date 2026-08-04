package com.dndmaster.aigamemaster.api;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import com.dndmaster.aigamemaster.infrastructure.ai.SpringAiChatAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Source-grounded fallback tracer for the supported simple resolution grammar. */
@RestController
public final class ResolutionCandidateController {
    private static final Logger log = LoggerFactory.getLogger(ResolutionCandidateController.class);
    private static final Pattern EXPLICIT_DC = Pattern.compile(
            "(?i)\\bDC\\s*(\\d+)\\s+([A-Za-z]+(?:\\s*\\([^)]*\\))?)\\s+(saving throw|sa|check)");
    private static final Pattern DICE = Pattern.compile("(?i)\\b(\\d+d\\d+(?:\\s*[+-]\\s*\\d+)?)\\b");
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
                + "Return exactly a JSON array, never an object wrapper. Use these exact enum values and field types. "
                + "Template: [{\"kind\":\"SAVING_THROW\",\"abilityOrSkill\":\"Dexterity\",\"dc\":12,\"diceExpression\":\"1d10\",\"visibility\":\"GM_REFERENCE\",\"sourceQuote\":\"exact quote\",\"sourceRefs\":[{\"documentId\":\"uuid\",\"extractionVersion\":2,\"locator\":\"offset 0-10\"}],\"detail\":null,\"provenance\":\"source text\"}]. "
                + "kind must be one of SKILL_ABILITY_CHECK,SAVING_THROW,PASSIVE_THRESHOLD,DICE_ROLL,ATTACK_ROLL,DAMAGE_ROLL,HEALING_ROLL,OPPOSED_CHECK,INITIATIVE_ROLL,RECHARGE_ROLL,RANDOM_TABLE,SPECIAL_ROLL. "
                + "visibility must be GM_REFERENCE or PLAYER_SAFE. Keep sourceRefs only when the excerpt supplies an exact object reference. "
                + "Do not invent values or references. Output JSON only. Excerpts: " + request.excerpts();
        List<Candidate> candidates = adapter.complete(request.operationId(), prompt, this::parseModel);
        if (!candidates.isEmpty()) {
            log.info("resolution_candidate_ai_result operationId={} aiCandidates={} excerpts={}", request.operationId(), candidates.size(), request.excerpts().size());
            return new Response(candidates);
        }
        List<Candidate> fallback = fallbackCandidates(request.excerpts());
        log.warn("resolution_candidate_ai_empty operationId={} fallbackCandidates={} excerpts={} excerptSummaries={}", request.operationId(), fallback.size(), request.excerpts().size(), request.excerpts().stream().map(e -> e.locator() + ":" + (e.text() == null ? 0 : e.text().length()) + ":" + (e.text() == null ? "" : e.text().substring(0, Math.min(100, e.text().length())).replaceAll("\\s+", " "))).toList());
        return new Response(fallback);
    }

    List<Candidate> parseModel(String text) {
        try {
            JsonNode root = objectMapper.readTree(extractJsonArray(text));
            if (!root.isArray()) throw new IllegalArgumentException("AI resolution response must be an array");
            List<Candidate> candidates = new ArrayList<>();
            for (JsonNode node : root) {
                try {
                    String kind = normalizeKind(text(node, "kind"));
                    if (!List.of("SKILL_ABILITY_CHECK", "SAVING_THROW", "PASSIVE_THRESHOLD", "DICE_ROLL",
                            "ATTACK_ROLL", "DAMAGE_ROLL", "HEALING_ROLL", "OPPOSED_CHECK",
                            "INITIATIVE_ROLL", "RECHARGE_ROLL", "RANDOM_TABLE", "SPECIAL_ROLL").contains(kind)) {
                        throw new IllegalArgumentException("unsupported resolution kind");
                    }
                    String visibility = normalizeVisibility(nullableText(node, "visibility"));
                    if (!List.of("GM_REFERENCE", "PLAYER_SAFE").contains(visibility)) {
                        throw new IllegalArgumentException("unsupported visibility");
                    }
                    candidates.add(new Candidate(kind, nullableText(node, "abilityOrSkill"), nullableInt(node, "dc"),
                            nullableText(node, "diceExpression"), visibility, text(node, "sourceQuote"),
                            parseRefs(node.get("sourceRefs")), objectOrNull(node.get("detail")), text(node, "provenance")));
                } catch (RuntimeException ignored) {
                    // One malformed model item must not discard valid source-grounded candidates.
                }
            }
            return List.copyOf(candidates);
        } catch (Exception malformed) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI resolution response was malformed", malformed);
        }
    }

    static List<Candidate> fallbackCandidates(List<Excerpt> excerpts) {
        if (excerpts == null) return List.of();
        List<Candidate> candidates = new ArrayList<>();
        for (Excerpt excerpt : excerpts) {
            if (excerpt == null || excerpt.text() == null) continue;
            Matcher matcher = EXPLICIT_DC.matcher(excerpt.text());
            while (matcher.find()) {
                String quote = matcher.group();
                Matcher dice = DICE.matcher(excerpt.text().substring(matcher.start()));
                String expression = dice.find() ? dice.group(1) : null;
                String kind = matcher.group(3).toLowerCase().startsWith("sa")
                        ? "SAVING_THROW" : "SKILL_ABILITY_CHECK";
                candidates.add(new Candidate(kind, matcher.group(2), Integer.valueOf(matcher.group(1)), expression,
                        "GM_REFERENCE", quote,
                        List.of(new SourceRef(excerpt.documentId(), excerpt.extractionVersion(), excerpt.locator())),
                        null, "deterministic-source-pattern-v1"));
            }
        }
        return List.copyOf(candidates);
    }

    private String extractJsonArray(String response) {
        String text = response == null ? "" : response.trim();
        try {
            JsonNode root = objectMapper.readTree(text);
            if (root.isArray()) return root.toString();
            if (root.isObject()) {
                for (String field : List.of("response", "output", "content", "result")) {
                    JsonNode value = root.get(field);
                    if (value == null) continue;
                    if (value.isArray()) return value.toString();
                    if (value.isTextual()) return extractJsonArray(value.textValue());
                }
            }
        } catch (Exception ignored) {
            // Fall through for markdown or escaped model output.
        }
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start < 0 || end < start) throw new IllegalArgumentException("AI response did not contain a JSON array");
        String array = text.substring(start, end + 1);
        try { objectMapper.readTree(array); return array; }
        catch (Exception escaped) { return array.replace("\\\"", "\""); }
    }

    private static String normalizeKind(String value) {
        String normalized = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "CHECK", "ABILITY_CHECK", "SKILL_CHECK" -> "SKILL_ABILITY_CHECK";
            case "SAVINGTHROW", "SAVE", "SAVING_THROW" -> "SAVING_THROW";
            case "DAMAGE" -> "DAMAGE_ROLL";
            case "ATTACK" -> "ATTACK_ROLL";
            default -> normalized;
        };
    }

    private static String normalizeVisibility(String value) {
        if (value == null || value.isBlank()) return "GM_REFERENCE";
        return switch (value.trim().toUpperCase()) {
            case "PUBLIC", "GM", "GM_REFERENCE", "FAILURE", "SUCCESS" -> "GM_REFERENCE";
            case "PLAYER", "PLAYER_SAFE" -> "PLAYER_SAFE";
            default -> value.trim().toUpperCase();
        };
    }

    private static JsonNode objectOrNull(JsonNode value) {
        return value != null && value.isObject() ? value : null;
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
        return java.util.stream.StreamSupport.stream(node.spliterator(), false).filter(JsonNode::isObject).flatMap(ref -> {
            try { return java.util.stream.Stream.of(new SourceRef(UUID.fromString(text(ref, "documentId")), ref.get("extractionVersion").asLong(), text(ref, "locator"))); }
            catch (RuntimeException malformed) { return java.util.stream.Stream.empty(); }
        }).toList();
    }

    public record Request(String operationId, List<Excerpt> excerpts, String schemaVersion, String promptVersion) {}
    public record Excerpt(UUID documentId, long extractionVersion, String locator, String text) {}
    public record Response(List<Candidate> candidates) {}
    public record Candidate(String kind, String abilityOrSkill, Integer dc, String diceExpression,
                            String visibility, String sourceQuote, List<SourceRef> sourceRefs, JsonNode detail, String provenance) {}
    public record SourceRef(UUID documentId, long extractionVersion, String locator) {}
}
