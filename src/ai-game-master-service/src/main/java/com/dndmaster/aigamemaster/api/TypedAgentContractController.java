package com.dndmaster.aigamemaster.api;

import com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Role-specific, read-only AI boundaries. The AI process receives no tool,
 * database, filesystem, or generic HTTP capability through these contracts.
 */
@RestController
public final class TypedAgentContractController {
    private final GmCompletionAdapter adapter;
    private final ObjectMapper mapper;
    private final ApiRequestGuard requestGuard;

    @Autowired
    public TypedAgentContractController(GmCompletionAdapter adapter, ObjectMapper mapper,
            @Value("${INTERNAL_SERVICE_TOKEN:typed-agent-local-token}") String internalToken) {
        this(adapter, mapper, new ApiRequestGuard(internalToken));
    }

    public TypedAgentContractController(GmCompletionAdapter adapter, ObjectMapper mapper, ApiRequestGuard requestGuard) {
        this.adapter = Objects.requireNonNull(adapter, "adapter must not be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.requestGuard = Objects.requireNonNull(requestGuard, "request guard must not be null");
    }

    @PostMapping("/internal/gm/scenario-compilation")
    ScenarioCompilationResponse scenarioCompilation(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody ScenarioCompilationRequest request) {
        requestGuard.internal(token);
        require(request);
        return adapter.complete(request.operationKey(),
                "ROLE=SCENARIO_COMPILATION\nSTORYBOOK_CONTEXT=" + request.storybookContext(),
                json -> parseCompilation(json));
    }

    @PostMapping("/internal/gm/scenario-lookup")
    ScenarioLookupResponse scenarioLookup(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody ScenarioLookupRequest request) {
        requestGuard.internal(token);
        require(request);
        return adapter.complete("scenario-lookup:" + request.query(),
                "ROLE=SCENARIO_LOOKUP\nREAD_ONLY_LOCKED_SCENARIO_MODEL=" + write(request.lockedScenarioModel())
                        + "\nQUERY=" + request.query(),
                this::parseLookup);
    }

    @PostMapping("/internal/gm/runtime-turn")
    RuntimeTurnResponse runtimeTurn(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody RuntimeTurnRequest request) {
        requestGuard.internal(token);
        require(request);
        return adapter.complete(request.operationKey(),
                        "ROLE=RUNTIME_GM\nCOMPOSITE_FACT_LOOKUP_RESULTS=" + write(request.factLookupResults())
                        + "\nRUNTIME_CONTEXT=" + write(request.runtimeContext())
                        + "\nACTION=" + request.action()
                        + "\nLOOKUP_ORDER_RULE=Use authoritative results in this order: Game State, established Runtime-added Facts, locked Scenario Model, then Storybook RAG. If all are NOT_FOUND, create only the minimum Runtime Fact needed to keep this turn playable. Do not use a lower-priority answer to contradict a higher-priority result."
                        + "\nGROUNDING_RULES=Distinguish Canonical Fact from Runtime Fact. Canonical Fact means a scenario truth such as a culprit, secret route, cause, hidden clue, or encounter structure; create or reveal it only when the locked ScenarioModel or selected evidence supports it. Runtime Fact means a play-created NPC reaction, opinion, refusal, negotiation, or compatible offer; it may be improvised when it does not contradict canonical truth, and any saved Runtime Fact must be treated as existing on later turns."
                        + "\nDIALOGUE_RULE=ACTION is an executed dialogue action, not a suggestion. If ACTION directly addresses an NPC, generate the NPC reaction in the current response. Do not tell the player to ask the same question again, and do not turn a completed question into an instruction. If a canonical answer is unavailable, respond diegetically through the NPC's ignorance, refusal, evasion, or negotiation; never say that the scenario lacks the information."
                        + "\nANTI_LOOP_RULE=Do not repeat the same dialogue action as a next choice or recommendation. Use a follow-up action such as negotiating a different condition, accepting or refusing an offer, asking a new question, or observing the surroundings."
                        + "\nOUTPUT_CONTRACT=Return exactly one JSON object with scene, judgment, narration, situation, combatStart, combatEnemies, mapEntryRequested, and optional runtimeFacts. "
                        + "situation must contain kind (CONTINUE or TRANSITION), location, problem, threat, goal, basis (SCENARIO, RAG, or FALLBACK), reference, and required. "
                        + "Choose the situation basis in this order: an applicable ScenarioModel element; otherwise a matching storybook RAG citation; otherwise FALLBACK only when a new fact is necessary to keep play moving, with required=true. "
                        + "For SCENARIO, reference is a ScenarioModel element id. For RAG, reference is a citationKey or locator from COMPOSITE_FACT_LOOKUP_RESULTS. For FALLBACK, reference is empty. "
                        + "The next GM turn receives this saved situation, so make it concrete and playable. Decide combat from the saved situation and the evidence, never from a word in the player's action. "
                        + "MANDATORY: if a hostile creature already supported by the saved situation is attacking, has cornered the party, or the player is exchanging attacks with it, return combatStart=true and a SITUATION enemy entry in the same response. "
                        + "Do not narrate a supported hostile creature attacking, closing in to attack, or 'combat ready' while returning combatStart=false. This is an output validity rule, not a discretionary pacing choice. "
                        + "mapEntryRequested must be a boolean. Set it to true only when the committed situation places the party inside the prepared map area and the player should see that map now; set it to false while the party is still outside, approaching, or when no prepared map applies. Base this on the saved situation and scenario context, not on keyword matching. "
                        + "runtimeFacts is optional. Include it only for a newly established playthrough fact created by this turn's compatible NPC reaction, refusal, offer, or negotiation after all authoritative lookup results are NOT_FOUND. Each item must contain subject and content. Never use runtimeFacts for a culprit, secret route, cause, hidden clue, puzzle answer, or other canonical scenario truth. "
                        + "When ACTION is SESSION_OPENING, LANGUAGE_CONTRACT requires all player-visible text in scene, judgment, narration, and situation to be written only in natural Korean. Do not output English or any other foreign-language words, labels, headings, or meta-commentary. Translate common nouns, class names, location names, action prompts, and proper names into Korean. Make the first player-facing narration establish the current location and why the party is here, state the immediate problem or pressure, identify a few observable things the party can respond to, and end with a Korean question inviting the player's action, such as '어떻게 하시겠어요?'. Use only RUNTIME_CONTEXT and COMPOSITE_FACT_LOOKUP_RESULTS; never reveal a puzzle answer or hidden fact. "
                        + "A player action is not evidence that an entity exists. Only enter combat with a combat scenario id present in RUNTIME_CONTEXT or COMPOSITE_FACT_LOOKUP_RESULTS. "
                        + "combatEnemies must always be an array of objects with mode (SCENARIO, SITUATION, or INSTANT), scenarioId, enemyKey, name, and positive count; "
                        + "SCENARIO requires a scenarioId from the current ScenarioModel. SITUATION leaves scenarioId empty and requires matching storybook RAG evidence for the current situation. INSTANT leaves scenarioId empty and is reserved for a GM-forced consequence such as noise or a critical failure. "
                        + "Use [] when combatStart is false. Never invent an enemy from the action alone. "
                        + "Do not use markdown, code fences, or any other text.",
                json -> parseRuntimeTurn(json, "SESSION_OPENING".equalsIgnoreCase(request.action())));
    }

    @PostMapping("/internal/gm/narration-safety")
    NarrationSafetyResponse narrationSafety(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody NarrationSafetyRequest request) {
        requestGuard.internal(token);
        require(request);
        return adapter.complete("narration-safety", "ROLE=NARRATION_SAFETY\nNARRATION=" + request.narration()
                        + "\nDISCLOSED_FACT_IDS=" + write(request.disclosedFactIds()), this::parseSafety);
    }

    private ScenarioCompilationResponse parseCompilation(String json) {
        JsonNode root = readObject(json);
        return new ScenarioCompilationResponse(required(root, "status"),
                root.path("scenarioModel").isObject()
                        ? mapper.convertValue(root.path("scenarioModel"), new TypeReference<Map<String, Object>>() { })
                        : Map.of());
    }

    private ScenarioLookupResponse parseLookup(String json) {
        JsonNode root = readObject(json);
        List<String> ids = root.has("supportingElementIds")
                ? mapper.convertValue(root.path("supportingElementIds"), mapper.getTypeFactory()
                        .constructCollectionType(List.class, String.class))
                : List.of();
        String status = required(root, "status");
        if (!status.equals("FOUND") && !status.equals("NOT_FOUND")) throw new IllegalArgumentException("invalid lookup status");
        return new ScenarioLookupResponse(status, root.path("answer").asText(""), ids);
    }

    private RuntimeTurnResponse parseRuntimeTurn(String json, boolean opening) {
        JsonNode root = readObject(json);
        String scene = required(root, "scene");
        String judgment = required(root, "judgment");
        String narration = required(root, "narration");
        if (opening) {
            requireKoreanPlayerText("scene", scene);
            requireKoreanPlayerText("judgment", judgment);
            requireKoreanPlayerText("narration", narration);
        }
        if (!root.has("combatStart") || !root.path("combatStart").isBoolean()) {
            throw new IllegalArgumentException("combatStart is required and must be boolean");
        }
        if (!root.has("mapEntryRequested") || !root.path("mapEntryRequested").isBoolean()) {
            throw new IllegalArgumentException("mapEntryRequested is required and must be boolean");
        }
        JsonNode enemiesNode = root.path("combatEnemies");
        if (!enemiesNode.isArray()) throw new IllegalArgumentException("combatEnemies is required and must be an array");
        List<CombatEnemyResponse> enemies = new java.util.ArrayList<>();
        for (JsonNode enemy : enemiesNode) {
            if (!enemy.isObject()) throw new IllegalArgumentException("combatEnemies entries must be objects");
            int count = enemy.path("count").asInt(0);
            if (count < 1) throw new IllegalArgumentException("combatEnemies count must be positive");
            String mode = enemy.path("mode").asText("SCENARIO").toUpperCase(java.util.Locale.ROOT);
            if (!mode.equals("SCENARIO") && !mode.equals("SITUATION") && !mode.equals("INSTANT")) {
                throw new IllegalArgumentException("invalid combat enemy mode");
            }
            String scenarioId = enemy.path("scenarioId").asText("").trim();
            if (mode.equals("SCENARIO") && scenarioId.isBlank()) {
                throw new IllegalArgumentException("scenarioId is required for a SCENARIO combat enemy");
            }
            String name = required(enemy, "name");
            if (opening) requireKoreanPlayerText("combatEnemies.name", name);
            enemies.add(new CombatEnemyResponse(mode, scenarioId, required(enemy, "enemyKey"), name, count));
        }
        boolean combatStart = root.path("combatStart").booleanValue();
        if (combatStart && enemies.isEmpty()) {
            throw new IllegalArgumentException("combatStart requires at least one structured combat enemy");
        }
        JsonNode situation = root.path("situation");
        if (!situation.isObject()) throw new IllegalArgumentException("situation is required and must be an object");
        String basis = required(situation, "basis").toUpperCase(java.util.Locale.ROOT);
        if (!basis.equals("SCENARIO") && !basis.equals("RAG") && !basis.equals("FALLBACK")) {
            throw new IllegalArgumentException("invalid situation basis");
        }
        if (!situation.has("required") || !situation.path("required").isBoolean()) {
            throw new IllegalArgumentException("situation required is mandatory");
        }
        String kind = required(situation, "kind").toUpperCase(java.util.Locale.ROOT);
        if (!kind.equals("CONTINUE") && !kind.equals("TRANSITION")) throw new IllegalArgumentException("invalid situation kind");
        String location = required(situation, "location");
        String problem = required(situation, "problem");
        String threat = required(situation, "threat");
        String goal = required(situation, "goal");
        if (opening) {
            requireKoreanPlayerText("situation.location", location);
            requireKoreanPlayerText("situation.problem", problem);
            requireKoreanPlayerText("situation.threat", threat);
            requireKoreanPlayerText("situation.goal", goal);
        }
        SituationResponse response = new SituationResponse(kind, location, problem, threat, goal, basis, situation.path("reference").asText(""),
                situation.path("required").booleanValue());
        List<RuntimeFactResponse> runtimeFacts = new java.util.ArrayList<>();
        JsonNode runtimeFactsNode = root.path("runtimeFacts");
        if (!runtimeFactsNode.isMissingNode()) {
            if (!runtimeFactsNode.isArray()) throw new IllegalArgumentException("runtimeFacts must be an array when provided");
            for (JsonNode fact : runtimeFactsNode) {
                if (!fact.isObject()) throw new IllegalArgumentException("runtimeFacts entries must be objects");
                String subject = required(fact, "subject");
                String content = required(fact, "content");
                if (opening) {
                    requireKoreanPlayerText("runtimeFacts.subject", subject);
                    requireKoreanPlayerText("runtimeFacts.content", content);
                }
                runtimeFacts.add(new RuntimeFactResponse(subject, content));
            }
        }
        return new RuntimeTurnResponse(scene, judgment, narration,
                combatStart, List.copyOf(enemies), response, root.path("mapEntryRequested").booleanValue(), List.copyOf(runtimeFacts));
    }

    private NarrationSafetyResponse parseSafety(String json) {
        JsonNode root = readObject(json);
        if (!root.has("approved") || !root.path("approved").isBoolean()) throw new IllegalArgumentException("approved is required");
        return new NarrationSafetyResponse(root.path("approved").booleanValue(), root.path("reason").asText(""));
    }

    private JsonNode readObject(String json) {
        try {
            JsonNode node = mapper.readTree(json);
            if (node == null || !node.isObject()) throw new IllegalArgumentException("typed agent response must be an object");
            return node;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid typed agent response", e);
        }
    }

    private static String required(JsonNode root, String field) {
        String value = root.path(field).asText("").trim();
        if (value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    private static void requireKoreanPlayerText(String field, String value) {
        boolean hasKoreanLetter = value.codePoints().anyMatch(TypedAgentContractController::isKoreanLetter);
        boolean hasForeignLetter = value.codePoints()
                .anyMatch(codePoint -> Character.isLetter(codePoint) && !isKoreanLetter(codePoint));
        if (!hasKoreanLetter || hasForeignLetter) throw new IllegalArgumentException(field + " must be written in Korean");
    }

    private static boolean isKoreanLetter(int codePoint) {
        return (codePoint >= 0x1100 && codePoint <= 0x11FF)
                || (codePoint >= 0x3130 && codePoint <= 0x318F)
                || (codePoint >= 0xA960 && codePoint <= 0xA97F)
                || (codePoint >= 0xAC00 && codePoint <= 0xD7A3)
                || (codePoint >= 0xD7B0 && codePoint <= 0xD7FF);
    }

    private String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalArgumentException("typed request serialization failed", e); }
    }

    private static void require(Object request) {
        if (request == null) throw new IllegalArgumentException("typed agent request is required");
    }

    public record ScenarioCompilationRequest(String operationKey, String storybookContext) {
        public ScenarioCompilationRequest {
            operationKey = required(operationKey, "operationKey");
            storybookContext = required(storybookContext, "storybookContext");
        }
    }

    public record ScenarioLookupRequest(String query, Map<String, Object> lockedScenarioModel) {
        public ScenarioLookupRequest {
            query = required(query, "query");
            lockedScenarioModel = Map.copyOf(Objects.requireNonNull(lockedScenarioModel, "lockedScenarioModel is required"));
        }
    }

    public record RuntimeTurnRequest(String operationKey, String action, List<Map<String, Object>> factLookupResults,
                                     Map<String, Object> runtimeContext) {
        public RuntimeTurnRequest(String operationKey, String action, List<Map<String, Object>> factLookupResults) {
            this(operationKey, action, factLookupResults, Map.of());
        }

        public RuntimeTurnRequest {
            operationKey = required(operationKey, "operationKey");
            action = required(action, "action");
            factLookupResults = List.copyOf(Objects.requireNonNull(factLookupResults, "factLookupResults is required"));
            runtimeContext = Map.copyOf(Objects.requireNonNull(runtimeContext, "runtimeContext is required"));
        }
    }

    public record NarrationSafetyRequest(String narration, List<String> disclosedFactIds) {
        public NarrationSafetyRequest {
            narration = required(narration, "narration");
            disclosedFactIds = List.copyOf(Objects.requireNonNull(disclosedFactIds, "disclosedFactIds is required"));
        }
    }

    public record ScenarioCompilationResponse(String status, Map<String, Object> scenarioModel) { }
    public record ScenarioLookupResponse(String status, String answer, List<String> supportingElementIds) { }
    public record RuntimeTurnResponse(String scene, String judgment, String narration, boolean combatStart,
                                      List<CombatEnemyResponse> combatEnemies, SituationResponse situation,
                                      boolean mapEntryRequested, List<RuntimeFactResponse> runtimeFacts) {
        public RuntimeTurnResponse(String scene, String judgment, String narration, boolean combatStart,
                List<CombatEnemyResponse> combatEnemies, SituationResponse situation, boolean mapEntryRequested) {
            this(scene, judgment, narration, combatStart, combatEnemies, situation, mapEntryRequested, List.of());
        }
        public RuntimeTurnResponse {
            runtimeFacts = runtimeFacts == null ? List.of() : List.copyOf(runtimeFacts);
        }
    }
    public record CombatEnemyResponse(String mode, String scenarioId, String enemyKey, String name, int count) { }
    public record SituationResponse(String kind, String location, String problem, String threat, String goal,
                                    String basis, String reference, boolean required) { }
    public record RuntimeFactResponse(String subject, String content) { }
    public record NarrationSafetyResponse(boolean approved, String reason) { }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
