package com.dndmaster.aigamemaster.api;

import com.dndmaster.aigamemaster.infrastructure.ai.SpringAiChatAdapter;
import com.dndmaster.aigamemaster.infrastructure.ai.CodexCliStoryPlanAdapter;
import com.dndmaster.aigamemaster.application.endpoint.AgentEndpoint;
import com.dndmaster.aigamemaster.application.endpoint.AgentEndpointRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Value;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Generates a source-aware outline. JSON is validated before crossing the service boundary. */
@RestController("aiAdventureStoryPlanController")
public final class AdventureStoryPlanController {
    private final SpringAiChatAdapter adapter; private final ObjectMapper mapper; private final AgentEndpointRegistry endpointRegistry;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final URI ollamaBaseUrl; private final String ollamaModel;
    private final String codexExecutable; private final java.nio.file.Path codexWorkDirectory; private final Duration codexTimeout;
    public AdventureStoryPlanController(SpringAiChatAdapter adapter, ObjectMapper mapper, AgentEndpointRegistry endpointRegistry,
            @Value("${local-ai.ollama.base-url:http://127.0.0.1:11434}") String ollamaBaseUrl,
            @Value("${local-ai.ollama.chat-model:qwen3:8b}") String ollamaModel,
            @Value("${ai.codex.executable:codex}") String codexExecutable,
            @Value("${ai.codex.work-directory:.}") String codexWorkDirectory,
            @Value("${ai.codex.timeout:PT5M}") Duration codexTimeout) {
        this.adapter = adapter; this.mapper = mapper; this.endpointRegistry = endpointRegistry; this.ollamaBaseUrl = URI.create(ollamaBaseUrl); this.ollamaModel = ollamaModel;
        this.codexExecutable = codexExecutable; this.codexWorkDirectory = java.nio.file.Path.of(codexWorkDirectory); this.codexTimeout = codexTimeout;
    }
    @PostMapping("/internal/v1/gm/adventure-story-plan")
    Response generate(@RequestBody Request request) {
        AgentEndpoint endpoint = endpointRegistry.active();
        Configuration configuration = request.configuration() == null ? Configuration.defaults() : request.configuration();
        String template = """
                # Adventure Plan

                ## Premise
                [one paragraph]

                ## Locations and Maps
                [locations, map assets, and how each map is used]

                ## Party Hooks
                [why this party is involved]

                ## Stage 1: [stage name]
                - Type: [dungeon | town | event]
                - Purpose: [combat | exploration | puzzle | social | travel | rest]
                - Location: [location]
                - Entry condition: [condition]
                - Exit condition: [condition]
                - Goal: [goal]
                - Enemies: [enemies]
                - Boss: [boss or none]
                - Clear condition: [condition]
                - Failure condition: [condition]
                - Rewards: [rewards]
                - Branches: [branch choices and destinations]
                - Map definition ID: [exact supplied mapDefinitionId or none]
                - Map asset: [map filename, page, or none]
                - Map locator: [exact supplied assetLocator or none]
                - Map usage: [tactical map | reference image | no map]
                - Player spawn: [semantic area or coordinates]
                - Enemy placement: [enemy, count, semantic area or coordinates]
                - Boss placement: [semantic area or coordinates]
                - NPC placement: [name and semantic area or coordinates]
                - Interactive objects: [objects and locations]
                - Hazards: [hazards and affected areas]
                - State flags set: [flags]
                - State flags required: [flags]
                - Required checks:
                  - Trigger: [when the roll is requested]
                  - Check type: [ability check | skill check | saving throw | attack | initiative | none]
                  - Ability or skill: [name or none]
                  - DC or dice: [only if supported by evidence; otherwise GM adjudication]
                  - Success: [result]
                  - Failure: [result or fail-forward consequence]
                - Source notes: [grounding from supplied documents]

                ## Stage 2: [stage name]
                [repeat the exact fields above for every stage]

                ## Ending 1: [ending name]
                - Resolution: [what happens]
                - Requirements: [what must be true]
                - Rewards: [final rewards]

                ## Ending 2: [ending name]
                - Resolution: [what happens]
                - Requirements: [what must be true]
                - Rewards: [final rewards]
                """;
        String availableMaps = request.maps().isEmpty() ? "(no maps supplied)" : request.maps().stream()
                .map(map -> "- mapDefinitionId=" + map.mapDefinitionId() + ", assetId=" + map.assetId()
                        + ", assetLocator=" + map.assetLocator() + ", sourceLocator=" + map.sourceLocator()
                        + ", confidence=" + map.confidence() + ", safetyStatus=" + map.safetyStatus()
                        + ", relatedStoryEvidence=" + map.relatedEvidence())
                .collect(java.util.stream.Collectors.joining("\n"));
        String prompt = "Create a source-grounded tabletop adventure plan by filling the Markdown template below. "
                + "All player-facing fields (title, location, goal, conflict, clues, enemies, rewards, conditions, and endings) MUST be written in natural Korean. Keep proper nouns in Korean where possible and never mix English prose into player-facing text. "
                + "Return the completed Markdown document only. Replace every bracketed placeholder with concrete content; do not leave placeholders. "
                + "Keep the headings and field labels stable so another agent can read the plan. "
                + "Create " + configuration.minimumStages() + "-" + configuration.maximumStages() + " stages and exactly " + configuration.endingCount() + " endings; duplicate or remove the sample stage/ending sections as needed. "
                + "MAP CONTRACT (mandatory): the AVAILABLE MAPS block below is authoritative. For every stage whose Type is dungeon, write a non-empty Map definition ID field using one exact mapDefinitionId from that block. Also copy the matching Map asset and Map locator fields. Never write none, blank, or an invented ID for a dungeon stage. A dungeon stage without these three map fields is invalid and must be corrected before returning the document. Non-dungeon stages may use no map. "
                + "For every mapped dungeon, use the supplied map image/page and the supplied story evidence to infer the party's starting area. In Player spawn, write the semantic entrance and grid coordinates when the map grid makes them identifiable; otherwise state 'GM confirmation required' and explain the evidence. Do not silently default to (0,0). "
                + "Do not invent named rules, DCs, monsters, or facts absent from evidence. For a check without an evidenced DC, write 'GM adjudication' rather than inventing a number. Include checks only when a trigger exists. Documents=" + request.sourceDocuments()
                + " Evidence=" + request.resolutionEvidence() + " citations=" + request.citations() + " maps=" + request.maps()
                + " partySize=" + request.partySize() + " configuration=" + configuration + "\n\nAVAILABLE MAPS (authoritative):\n" + availableMaps
                + "\n\nTEMPLATE:\n" + template;
        try {
            if (endpoint.provider() == AgentEndpoint.Provider.CODEX_CLI) {
                String response = new CodexCliStoryPlanAdapter(codexExecutable, endpoint.model(), codexWorkDirectory, codexTimeout)
                        .complete(request.operationId(), prompt);
                return new Response(parse(response, configuration));
            }
            URI baseUrl = endpoint.provider() == AgentEndpoint.Provider.OLLAMA ? endpoint.baseUrl() : ollamaBaseUrl;
            String model = endpoint.model().isBlank() ? ollamaModel : endpoint.model();
            String body = mapper.writeValueAsString(Map.of("model", model, "prompt", prompt,
                    "stream", false, "think", false, "options", Map.of("num_predict", 1200)));
            HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder(baseUrl.resolve("/api/generate"))
                    .timeout(Duration.ofMinutes(10)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("Ollama returned HTTP " + response.statusCode());
            JsonNode envelope = mapper.readTree(response.body());
            return new Response(parse(envelope.path("response").asText(), configuration));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("story plan generation interrupted via " + endpoint.provider(), e);
        } catch (Exception e) {
            throw new IllegalStateException("story plan generation failed via " + endpoint.provider() + ": " + rootMessage(e), e);
        }
    }

    /** Produces one typed, source-grounded tactical scene candidate for a mapped story-plan stage. */
    @PostMapping("/internal/v1/gm/tactical-scene-plan")
    JsonNode generateTacticalScene(@RequestBody JsonNode request) {
        String operationId = request.path("stage").path("position").asText("stage") + "-tactical-" + UUID.randomUUID();
        String prompt = """
                Produce exactly one JSON object for a tactical-scene candidate. Do not include Markdown.
                The response shape is {"stagePosition":number,"scene":{...},"citations":[...]}.
                scene MUST use schemaVersion 1 and status READY. It must contain boundary {minimum:{x,y},maximum:{x,y},forbiddenCoordinates:[]},
                players/allies/npcs/enemies/bosses/interactiveObjects/environments arrays, initialFog {hiddenRegions,grounding}, triggers, outcomes, and transitionIds.
                Every coordinate is normalized from 0 through 1. Every placement/environment must have id, kind, coordinate, and grounding.
                A grounding is either {type:"SOURCE_CITATION",citation:"documentUuid:locator",rationale:""} using ONLY a supplied citation,
                or {type:"AI_INFERENCE",citation:"",rationale:"bounded explanation tied to the supplied map or story evidence"}.
                Source citations and supplied map/story evidence take precedence. Never invent a named boss, reward, ending, monster, or map fact not present in them.
                If the prior validation feedback identifies a violation, correct that violation in this response.
                Request: """ + request;
        try {
            AgentEndpoint endpoint = endpointRegistry.active();
            String response;
            if (endpoint.provider() == AgentEndpoint.Provider.CODEX_CLI) {
                response = new CodexCliStoryPlanAdapter(codexExecutable, endpoint.model(), codexWorkDirectory, codexTimeout)
                        .complete(operationId, prompt);
                JsonNode candidate = parseTacticalCandidate(response);
                if (!candidate.has("stagePosition") || !candidate.has("scene")) {
                    throw new IllegalArgumentException("tactical candidate fields missing");
                }
                return candidate;
            } else {
                return adapter.completeWithModel(operationId, prompt, this::parseTacticalCandidate, endpoint.model());
            }
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI tactical scene response malformed", e);
        }
    }

    private JsonNode parseTacticalCandidate(String response) {
        try {
            JsonNode candidate = mapper.readTree(extractObject(response));
            if (!candidate.has("stagePosition") || !candidate.has("scene")) {
                throw new IllegalArgumentException("tactical candidate fields missing");
            }
            return candidate;
        } catch (Exception e) {
            throw new IllegalArgumentException("tactical candidate is not valid JSON", e);
        }
    }

    private static String rootMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null) root = root.getCause();
        return root.getMessage() == null || root.getMessage().isBlank() ? root.getClass().getSimpleName() : root.getMessage();
    }
    private List<Stage> parse(String text, Configuration configuration) {
        try {
            JsonNode root;
            try { root = mapper.readTree(extractObject(text)); }
            catch (Exception markdown) { return parseMarkdown(text, configuration); }
            JsonNode stages = root.get("stages");
            if (stages == null || !stages.isArray()) throw new IllegalArgumentException("stages missing");
            List<Stage> result = new ArrayList<>();
            for (JsonNode n : stages) {
                List<String> endings = strings(n.get("endingIds")); if (endings.isEmpty()) throw new IllegalArgumentException("endingIds missing");
                result.add(new Stage(n.path("position").asInt(result.size() + 1), required(n,"title"), text(n, "stageType", "EVENT"), text(n, "location", required(n, "title")), required(n,"goal"), required(n,"conflict"), required(n,"transitionCondition"), strings(n.get("npcOrClues")), endings,
                        text(n, "mapDefinitionId", ""), text(n, "mapAssetId", ""), text(n, "mapAssetLocator", ""), strings(n.get("enemies")), text(n, "boss", ""), text(n, "clearCondition", required(n, "transitionCondition")), text(n, "failureCondition", ""), strings(n.get("rewards")), strings(n.get("branchIds")), maps(n.get("branchTargets")), citations(n.get("evidence"))));
            }
            if (result.size() < configuration.minimumStages() || result.size() > configuration.maximumStages()) throw new IllegalArgumentException("invalid stage count");
            if (result.stream().flatMap(s -> s.endingIds().stream()).distinct().count() != configuration.endingCount()) throw new IllegalArgumentException("invalid ending count");
            return List.copyOf(result);
        } catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI story plan response malformed", e); }
    }
    List<Stage> parseMarkdown(String markdown, Configuration configuration) {
        String clean = markdown == null ? "" : markdown.replace("\r", "").trim();
        if (clean.isBlank()) throw new IllegalArgumentException("markdown response empty");
        java.util.regex.Matcher headings = java.util.regex.Pattern.compile("(?mi)^##\\s*Stage\\s*\\d+\\s*[:.-]?\\s*(.+?)\\s*$").matcher(clean);
        List<String> titles = new ArrayList<>(); List<Integer> starts = new ArrayList<>();
        while (headings.find()) { String title = headings.group(1).trim(); titles.add(title); starts.add(headings.start()); }
        if (titles.isEmpty()) throw new IllegalArgumentException("markdown stage headings missing");
        List<Stage> result = new ArrayList<>();
        for (int i = 0; i < titles.size(); i++) {
            int from = starts.get(i), to = i + 1 < starts.size() ? starts.get(i + 1) : clean.length();
            String body = clean.substring(from, to).trim();
            String ending = "ending-" + ((i % configuration.endingCount()) + 1);
            result.add(new Stage(i + 1, titles.get(i), value(body, "Type", "EVENT").toUpperCase(java.util.Locale.ROOT), value(body, "Location", titles.get(i)), value(body, "Goal", firstLine(body)),
                    value(body, "Conflict", body), value(body, "Clear condition", "Continue when the stage goal is achieved"),
                    placementNotes(body), List.of(ending), noneToBlank(value(body, "Map definition ID", "")),
                    noneToBlank(value(body, "Map asset", "")), noneToBlank(value(body, "Map locator", "")), split(value(body, "Enemies", "")),
                    value(body, "Boss", ""), value(body, "Clear condition", ""), value(body, "Failure condition", ""), split(value(body, "Rewards", "")), split(value(body, "Branches", "")), Map.of(), List.of()));
        }
        if (result.size() < configuration.minimumStages() || result.size() > configuration.maximumStages()) throw new IllegalArgumentException("invalid markdown stage count");
        return List.copyOf(result);
    }
    private static String firstLine(String body) {
        return java.util.Arrays.stream(body.split("\\n")).map(String::trim).filter(line -> !line.isBlank() && !line.startsWith("#") && !line.startsWith("-")).findFirst().orElse("Advance the adventure");
    }
    private static String value(String body, String label, String fallback) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?im)^\\s*-\\s*" + java.util.regex.Pattern.quote(label) + "\\s*:\\s*(.+?)\\s*$").matcher(body);
        return m.find() ? m.group(1).trim() : fallback;
    }
    private static List<String> split(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("none")) return List.of();
        return java.util.Arrays.stream(value.split("[,;]\\s*|\\s+\\+\\s+"))
                .map(String::trim).filter(s -> !s.isBlank()).toList();
    }
    private static String noneToBlank(String value) {
        return value == null || value.equalsIgnoreCase("none") ? "" : value.trim();
    }
    private static List<String> placementNotes(String body) {
        List<String> notes = new ArrayList<>();
        for (String label : List.of("Purpose", "Entry condition", "Exit condition", "Map asset", "Map usage", "Player spawn",
                "Enemy placement", "Boss placement", "NPC placement", "Interactive objects", "Hazards", "State flags set", "State flags required",
                "Trigger", "Check type", "Ability or skill", "DC or dice", "Success", "Failure", "Source notes")) {
            String value = value(body, label, "");
            if (!value.isBlank() && !value.startsWith("[")) notes.add("MAP_" + label.toUpperCase(java.util.Locale.ROOT).replace(' ', '_') + ": " + value);
        }
        return List.copyOf(notes);
    }
    private static String extractObject(String text) { int a = text.indexOf('{'), b = text.lastIndexOf('}'); if (a < 0 || b < a) throw new IllegalArgumentException("JSON object missing"); return text.substring(a,b+1); }
    private static String required(JsonNode n, String key) { String v = n.path(key).asText("").trim(); if (v.isBlank()) throw new IllegalArgumentException(key + " missing"); return v; }
    private static List<String> strings(JsonNode n) { if (n == null || !n.isArray()) return List.of(); List<String> r = new ArrayList<>(); n.forEach(v -> { if (v.isTextual() && !v.asText().isBlank()) r.add(v.asText()); }); return List.copyOf(r); }
    private static String text(JsonNode node, String key, String fallback) { String value = node.path(key).asText("").trim(); return value.isBlank() ? fallback : value; }
    private static Map<String, String> maps(JsonNode node) {
        if (node == null || !node.isObject()) return Map.of();
        Map<String, String> result = new HashMap<>();
        node.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().asText()));
        return Map.copyOf(result);
    }
    private static List<SourceCitation> citations(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<SourceCitation> result = new ArrayList<>();
        node.forEach(item -> {
            String documentType = text(item, "documentType", ""); String documentId = text(item, "documentId", "");
            String locator = text(item, "locator", ""); String quote = text(item, "quote", "");
            if (!documentType.isBlank() && !documentId.isBlank() && !locator.isBlank() && !quote.isBlank()) {
                result.add(new SourceCitation(documentType, documentId, item.path("extractionVersion").asLong(1), locator, quote, item.path("confidence").asDouble(.5)));
            }
        });
        return List.copyOf(result);
    }
    public record Request(String operationId, long packageRevision, int partySize, Configuration configuration, List<String> sourceDocuments,
            List<String> resolutionEvidence, List<MapContext> maps, List<SourceCitation> citations) {
        public Request(String operationId, long packageRevision, int partySize, Configuration configuration, List<String> sourceDocuments, List<String> resolutionEvidence) {
            this(operationId, packageRevision, partySize, configuration, sourceDocuments, resolutionEvidence, List.of(), List.of());
        }
    }
    public record MapContext(String mapDefinitionId, String assetId, String assetLocator, String sourceLocator, double confidence, String safetyStatus, List<String> relatedEvidence) {}
    public record SourceCitation(String documentType, String documentId, long extractionVersion, String locator, String quote, double confidence) {}
    public record Configuration(int endingCount, String adventureLength) {
        public Configuration {
            if (endingCount < 1 || endingCount > 4) throw new IllegalArgumentException("ending count must be between 1 and 4");
            if (!List.of("SHORT", "STANDARD", "LONG").contains(adventureLength)) throw new IllegalArgumentException("unknown adventure length");
        }
        static Configuration defaults() { return new Configuration(2, "STANDARD"); }
        int minimumStages() { return switch (adventureLength) { case "SHORT" -> 3; case "STANDARD" -> 4; default -> 7; }; }
        int maximumStages() { return switch (adventureLength) { case "SHORT" -> 4; case "STANDARD" -> 6; default -> 8; }; }
    }
    public record Response(List<Stage> stages) {}
    public record Stage(int position, String title, String stageType, String location, String goal, String conflict, String transitionCondition,
            List<String> npcOrClues, List<String> endingIds, String mapDefinitionId, String mapAssetId, String mapAssetLocator, List<String> enemies, String boss,
            String clearCondition, String failureCondition, List<String> rewards, List<String> branchIds, Map<String, String> branchTargets, List<SourceCitation> evidence) {}
}
