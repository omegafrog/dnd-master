package com.dndmaster.aigamemaster.api;

import java.util.List;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import com.dndmaster.aigamemaster.infrastructure.ai.SpringAiChatAdapter;
import com.dndmaster.aigamemaster.infrastructure.ai.CodexCliStoryPlanAdapter;
import com.dndmaster.aigamemaster.application.endpoint.AgentEndpoint;
import com.dndmaster.aigamemaster.application.endpoint.AgentEndpointRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Value;
import java.time.Duration;
import java.nio.file.Path;
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
            "(?i)\\bDC\\s*(\\d+)\\s+([A-Za-z]+(?:\\s*\\([^)]*\\))?)\\s+(sa\\s*ving\\s+throw(?:s)?|check(?:s)?)");
    private static final Pattern DICE = Pattern.compile("(?i)\\b(\\d+d\\d+(?:\\s*[+-]\\s*\\d+)?)\\b");
    private static final Pattern ANY_DC = Pattern.compile("(?i)\\bDC\\s*(\\d+)\\b");
    private final SpringAiChatAdapter adapter;
    private final ObjectMapper objectMapper;
    private final AgentEndpointRegistry endpointRegistry;
    private final String codexExecutable;
    private final Path codexWorkDirectory;
    private final Duration codexTimeout;

    /** Backward-compatible constructor for deterministic parser tests. */
    public ResolutionCandidateController(SpringAiChatAdapter adapter, ObjectMapper objectMapper) {
        this(adapter, objectMapper, null, "codex", ".", Duration.ofMinutes(5));
    }

    public ResolutionCandidateController(SpringAiChatAdapter adapter, ObjectMapper objectMapper,
            AgentEndpointRegistry endpointRegistry,
            @Value("${ai.codex.executable:codex}") String codexExecutable,
            @Value("${ai.codex.work-directory:.}") String codexWorkDirectory,
            @Value("${ai.codex.timeout:PT5M}") Duration codexTimeout) {
        this.adapter = adapter;
        this.objectMapper = objectMapper;
        this.endpointRegistry = endpointRegistry;
        this.codexExecutable = codexExecutable;
        this.codexWorkDirectory = Path.of(codexWorkDirectory);
        this.codexTimeout = codexTimeout;
    }

    @PostMapping("/internal/v1/gm/resolution-candidates")
    Response extract(@RequestBody Request request) {
        if (request == null || request.excerpts() == null) return new Response(List.of());
        String prompt = "Extract only directly supported tabletop resolution candidates from these source excerpts. "
                + (request.attempt() > 0 ? "This is retry attempt " + request.attempt() + ". Failed candidate: " + request.failedCandidate()
                        + ". Diagnostics: " + request.diagnostics() + ". Correct the diagnosed failure and return a replacement candidate. " : "")
                + "Return exactly a JSON array, never an object wrapper. Use these exact enum values and field types. "
                + "Template: [{\"kind\":\"SAVING_THROW\",\"abilityOrSkill\":\"Dexterity\",\"dc\":12,\"diceExpression\":\"1d10\",\"visibility\":\"GM_REFERENCE\",\"sourceQuote\":\"exact quote\",\"sourceRefs\":[{\"documentId\":\"uuid\",\"extractionVersion\":2,\"locator\":\"offset 0-10\"}],\"detail\":null,\"provenance\":\"source text\"}]. "
                + "kind must be one of SKILL_ABILITY_CHECK,SAVING_THROW,PASSIVE_THRESHOLD,DICE_ROLL,ATTACK_ROLL,DAMAGE_ROLL,HEALING_ROLL,OPPOSED_CHECK,INITIATIVE_ROLL,RECHARGE_ROLL,RANDOM_TABLE,SPECIAL_ROLL. "
                + "visibility must be GM_REFERENCE or PLAYER_SAFE. Keep sourceRefs only when the excerpt supplies an exact object reference. "
                + "Do not invent values or references. Output JSON only. Excerpts: " + request.excerpts();
        if (request.promptVersion() != null && request.promptVersion().startsWith("resolution-recovery")) {
            prompt += " This is a recovery pass for previously invalid candidates. Re-read the exact source text and repair only the invalid resolution. For RECHARGE_ROLL, extract a numeric inclusive range such as Recharge 5-6 into diceExpression as exactly 5-6; never leave diceExpression null when the source contains a recharge range. Preserve the source quote and source reference.";
        }
        List<Candidate> candidates;
        AgentEndpoint endpoint = endpointRegistry.active();
        if (endpoint.provider() == AgentEndpoint.Provider.CODEX_CLI) {
            String response = new CodexCliStoryPlanAdapter(codexExecutable, endpoint.model(), codexWorkDirectory, codexTimeout)
                    .complete(request.operationId(), prompt);
            candidates = parseModel(response);
        } else {
            candidates = adapter.complete(request.operationId(), prompt, this::parseModel);
        }
        if (!candidates.isEmpty()) {
            List<Candidate> verified = candidates.stream().filter(candidate -> verifiedAgainstExcerpts(candidate, request.excerpts())).toList();
            List<Candidate> deduplicated = deduplicate(verified);
            log.info("resolution_candidate_ai_result operationId={} aiCandidates={} deduplicated={} excerpts={}", request.operationId(), candidates.size(), deduplicated.size(), request.excerpts().size());
            return new Response(deduplicated);
        }
        List<Candidate> fallback = deduplicate(fallbackCandidates(request.excerpts()));
        log.warn("resolution_candidate_ai_empty operationId={} fallbackCandidates={} excerpts={} excerptSummaries={}", request.operationId(), fallback.size(), request.excerpts().size(), request.excerpts().stream().map(e -> e.locator() + ":" + (e.text() == null ? 0 : e.text().length()) + ":" + (e.text() == null ? "" : e.text().substring(0, Math.min(100, e.text().length())).replaceAll("\\s+", " "))).toList());
        return new Response(fallback);
    }

    private static boolean verifiedAgainstExcerpts(Candidate candidate, List<Excerpt> excerpts) {
        if (candidate == null || candidate.sourceQuote() == null || candidate.sourceQuote().isBlank()
                || candidate.sourceRefs() == null || candidate.sourceRefs().isEmpty()) return false;
        String quote = normalize(candidate.sourceQuote());
        return candidate.sourceRefs().stream().anyMatch(ref -> excerpts.stream().anyMatch(excerpt ->
                ref != null && excerpt != null && ref.documentId().equals(excerpt.documentId())
                        && ref.extractionVersion() == excerpt.extractionVersion()
                        && ref.locator().equals(excerpt.locator())
                        && normalize(excerpt.text()).contains(quote)));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().replaceAll("(?U)\\s+", " ").toLowerCase(Locale.ROOT);
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
                String quote = normalizeWhitespace(matcher.group());
                String expression = diceExpression(excerpt.text(), matcher.end());
                String kind = matcher.group(3).replaceAll("\\s+", "").toLowerCase().startsWith("saving")
                        ? "SAVING_THROW" : "SKILL_ABILITY_CHECK";
                candidates.add(new Candidate(kind, normalizeWhitespace(matcher.group(2)), Integer.valueOf(matcher.group(1)), expression,
                        "GM_REFERENCE", quote,
                        List.of(new SourceRef(excerpt.documentId(), excerpt.extractionVersion(), excerpt.locator())),
                        null, "deterministic-source-pattern-v1"));
            }
            if (candidates.stream().noneMatch(candidate -> candidate.sourceRefs().stream().anyMatch(ref -> ref.documentId().equals(excerpt.documentId()) && ref.extractionVersion() == excerpt.extractionVersion() && ref.locator().equals(excerpt.locator())))) {
                Matcher dc = ANY_DC.matcher(excerpt.text());
                if (dc.find()) {
                    int start = Math.max(0, excerpt.text().lastIndexOf('.', dc.start()) + 1);
                    int end = excerpt.text().indexOf('.', dc.end());
                    if (end < 0) end = Math.min(excerpt.text().length(), dc.end() + 240);
                    String quote = excerpt.text().substring(start, end + (end < excerpt.text().length() && excerpt.text().charAt(end) == '.' ? 1 : 0)).strip();
                    if (EXPLICIT_DC.matcher(quote).find()) {
                        String kind = quote.toLowerCase(Locale.ROOT).contains("saving throw") ? "SAVING_THROW" : "SKILL_ABILITY_CHECK";
                        candidates.add(new Candidate(kind, null, Integer.valueOf(dc.group(1)), diceExpression(excerpt.text(), dc.end()), "GM_REFERENCE", quote,
                                List.of(new SourceRef(excerpt.documentId(), excerpt.extractionVersion(), excerpt.locator())), null, "deterministic-source-pattern-v2"));
                    }
                }
            }
        }
        return List.copyOf(candidates);
    }

    static List<Candidate> deduplicate(List<Candidate> candidates) {
        LinkedHashMap<String, Candidate> unique = new LinkedHashMap<>();
        for (Candidate candidate : candidates) {
            if (candidate == null) continue;
            unique.putIfAbsent(candidateKey(candidate), candidate);
        }
        return List.copyOf(unique.values());
    }

    private static String candidateKey(Candidate candidate) {
        return String.join("|", normalized(candidate.kind()), normalized(candidate.abilityOrSkill()),
                normalized(candidate.dc()), normalized(candidate.diceExpression()), normalized(candidate.sourceQuote()));
    }

    private static String normalized(Object value) {
        return value == null ? "" : value.toString().replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private static String diceExpression(String text, int from) {
        int end = text.length();
        Matcher nextResolution = EXPLICIT_DC.matcher(text);
        nextResolution.region(Math.min(from, text.length()), text.length());
        if (nextResolution.find()) end = Math.min(end, nextResolution.start());
        for (int index = from; index < text.length() && index < from + 220; index++) {
            char current = text.charAt(index);
            if (current == '.' || current == '!' || current == '?') {
                end = index + 1;
                break;
            }
        }
        Matcher dice = DICE.matcher(text.substring(Math.min(from, text.length()), Math.min(end, text.length())));
        return dice.find() ? dice.group(1).replaceAll("\\s+", "") : null;
    }

    private static String normalizeWhitespace(String value) {
        return value == null ? null : value.replaceAll("\\s+", " ").trim();
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

    public record Request(String operationId, List<Excerpt> excerpts, String schemaVersion, String promptVersion,
                           JsonNode failedCandidate, int attempt, List<String> diagnostics) {
        public Request(String operationId, List<Excerpt> excerpts, String schemaVersion, String promptVersion) {
            this(operationId, excerpts, schemaVersion, promptVersion, null, 0, List.of());
        }
        public Request {
            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        }
    }
    public record Excerpt(UUID documentId, long extractionVersion, String locator, String text) {}
    public record Response(List<Candidate> candidates) {}
    public record Candidate(String kind, String abilityOrSkill, Integer dc, String diceExpression,
                            String visibility, String sourceQuote, List<SourceRef> sourceRefs, JsonNode detail, String provenance) {}
    public record SourceRef(UUID documentId, long extractionVersion, String locator) {}
}
