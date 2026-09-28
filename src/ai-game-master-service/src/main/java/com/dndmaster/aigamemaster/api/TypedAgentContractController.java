package com.dndmaster.aigamemaster.api;

import com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.dndmaster.aigamemaster.infrastructure.ai.RequestedGmProviderSelection;
import com.dndmaster.aigamemaster.infrastructure.ai.EffectiveGmProviderSelection;
import com.dndmaster.aigamemaster.infrastructure.ai.GmProviderSelectionResolver;
import com.dndmaster.aigamemaster.application.endpoint.AgentEndpointRegistry;
import java.util.ArrayList;
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
    private final java.util.function.Function<RequestedGmProviderSelection, GmProviderSelectionResolver.EndpointResolution> selectionResolver;

    @Autowired
    public TypedAgentContractController(GmCompletionAdapter adapter, ObjectMapper mapper,
            @Value("${ai-game-master.integration.internal-token:${INTERNAL_SERVICE_TOKEN:}}") String internalToken,
            AgentEndpointRegistry endpointRegistry) {
        this(adapter, mapper, new ApiRequestGuard(internalToken),
                new GmProviderSelectionResolver(endpointRegistry)::resolveEndpoint);
    }

    public TypedAgentContractController(GmCompletionAdapter adapter, ObjectMapper mapper, ApiRequestGuard requestGuard) {
        this(adapter, mapper, requestGuard, request -> testResolution(request));
    }

    TypedAgentContractController(GmCompletionAdapter adapter, ObjectMapper mapper, ApiRequestGuard requestGuard,
            int runtimeContextWindowTokens) {
        this(adapter, mapper, requestGuard, request -> testResolution(request));
    }

    TypedAgentContractController(GmCompletionAdapter adapter, ObjectMapper mapper, ApiRequestGuard requestGuard,
            java.util.function.Function<RequestedGmProviderSelection, GmProviderSelectionResolver.EndpointResolution> selectionResolver) {
        this.adapter = Objects.requireNonNull(adapter, "adapter must not be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.requestGuard = Objects.requireNonNull(requestGuard, "request guard must not be null");
        this.selectionResolver = Objects.requireNonNull(selectionResolver);
    }

    private static GmProviderSelectionResolver.EndpointResolution testResolution(RequestedGmProviderSelection request) {
        java.util.UUID id = request.endpointId() == null ? java.util.UUID.nameUUIDFromBytes((request.provider() + "/" + request.model()).getBytes(java.nio.charset.StandardCharsets.UTF_8)) : request.endpointId();
        java.time.Instant version = java.time.Instant.EPOCH;
        var provider = switch (request.provider()) {
            case "codex-cli" -> com.dndmaster.aigamemaster.application.endpoint.AgentEndpoint.Provider.CODEX_CLI;
            case "openai" -> com.dndmaster.aigamemaster.application.endpoint.AgentEndpoint.Provider.OPENAI_COMPATIBLE;
            default -> com.dndmaster.aigamemaster.application.endpoint.AgentEndpoint.Provider.OLLAMA;
        };
        var endpoint = new com.dndmaster.aigamemaster.application.endpoint.AgentEndpoint(id, "test", provider,
                java.net.URI.create("http://localhost"), request.model(), null, true, version);
        return new GmProviderSelectionResolver.EndpointResolution(endpoint,
                new EffectiveGmProviderSelection(id, version, request.provider(), request.model(), request.reasoning()));
    }

    @PostMapping("/internal/gm/scenario-compilation")
    ScenarioCompilationResponse scenarioCompilation(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody ScenarioCompilationRequest request) {
        requestGuard.internal(token);
        require(request);
        return adapter.complete(request.soloPlayerId(), request.operationKey(),
                "ROLE=SCENARIO_COMPILATION\nSTORYBOOK_CONTEXT=" + request.storybookContext()
                        + "\nTASK=Compile the source-backed hidden ScenarioModel from the supplied Storybook excerpts. "
                        + "Find essential combat encounters: named or identifiable hostile groups that the Storybook makes part of the objective, a required obstacle, or an explicit triggered fight. Do not include every passing mention of a creature, non-hostile NPCs, or invented encounters. "
                        + "For each encounter, preserve enemy identity, count, and location only when the Storybook states or clearly supports them. "
                        + "Each element in actors, locations, objectives, revelations, encounters, relationships, and resolutionCriteria must include elementId, type, attributes, and sourceRefs; type is a non-empty string and every sourceRefs entry must follow the specified shape. "
                        + "Every encounter must be an object in scenarioModel.encounters with type combat-scenario, elementId, attributes.enemyKey, attributes.displayName, attributes.count, attributes.location, and sourceRefs. "
                        + "Each sourceRefs item must exactly cite a supplied DOCUMENT_ID, EXTRACTION_VERSION, and LOCATOR using {knowledgeDocumentId:{value:DOCUMENT_ID},extractionVersion:EXTRACTION_VERSION,locator:LOCATOR}. Never invent or rewrite a source reference. "
                        + "Also provide the ScenarioModel schema fields schemaVersion, actors, locations, objectives, revelations, encounters, relationships, resolutionCriteria, and startingSituation. schemaVersion must be an integer JSON number and startingSituation must be a plain text string. "
                        + "OUTPUT_CONTRACT=Return exactly one JSON object with status (READY or BLOCKED), scenarioModel when READY, and diagnostics. Do not use markdown or add text outside JSON.",
                json -> parseCompilation(json));
    }

    @PostMapping("/internal/gm/scenario-lookup")
    ScenarioLookupResponse scenarioLookup(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody ScenarioLookupRequest request) {
        requestGuard.internal(token);
        require(request);
        return adapter.complete(request.soloPlayerId(), "scenario-lookup:" + request.query(),
                "ROLE=SCENARIO_LOOKUP\nREAD_ONLY_LOCKED_SCENARIO_MODEL=" + write(request.lockedScenarioModel())
                        + "\nQUERY=" + request.query()
                        + "\nOUTPUT_CONTRACT=Return exactly one JSON object with status, answer, and supportingElementIds. "
                        + "status must be FOUND or NOT_FOUND. answer must be a concise answer grounded in the locked ScenarioModel; "
                        + "use an empty answer and an empty supportingElementIds array for NOT_FOUND. "
                        + "supportingElementIds must contain only element ids present in the locked ScenarioModel. "
                        + "Do not use markdown, code fences, or any other text.",
                this::parseLookup);
    }

    @PostMapping("/internal/gm/runtime-endpoint")
    RuntimeEndpointResponse runtimeEndpoint(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody RuntimeEndpointRequest request) {
        requestGuard.internal(token);
        require(request);
        var resolution = selectionResolver.apply(new RequestedGmProviderSelection(
                request.endpointId(), request.provider(), request.model(), request.reasoning()));
        var effective = resolution.effectiveSelection();
        return new RuntimeEndpointResponse(effective.endpointId(), effective.endpointVersion().toString(),
                effective.provider(), effective.model(), effective.reasoning());
    }

    @PostMapping("/internal/gm/runtime-turn")
    RuntimeTurnResponse runtimeTurn(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody RuntimeTurnRequest request) {
        requestGuard.internal(token);
        require(request);
        RequestedGmProviderSelection requested = new RequestedGmProviderSelection(request.endpointId(), request.provider(), request.model(), request.reasoning());
        GmProviderSelectionResolver.EndpointResolution resolution = selectionResolver.apply(requested);
        EffectiveGmProviderSelection effective = resolution.effectiveSelection();
        if (!effective.endpointId().equals(request.effectiveEndpointId())
                || !effective.endpointVersion().toString().equals(request.effectiveEndpointVersion())
                || !effective.provider().equals(request.effectiveProvider())
                || !effective.model().equals(request.effectiveModel())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "selected GM endpoint changed before execution");
        }
        return adapter.completeWithResolution(request.soloPlayerId(), request.operationKey(), request.prompt(),
                json -> parseRuntimeTurn(json, "SESSION_OPENING".equalsIgnoreCase(request.action())),
                requested, resolution).response();
    }

    @PostMapping("/internal/gm/narration-safety")
    NarrationSafetyResponse narrationSafety(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody NarrationSafetyRequest request) {
        requestGuard.internal(token);
        require(request);
        return adapter.complete(request.soloPlayerId(), "narration-safety", "ROLE=NARRATION_SAFETY\nNARRATION=" + request.narration()
                        + "\nDISCLOSED_FACT_IDS=" + write(request.disclosedFactIds()), this::parseSafety);
    }

    @PostMapping("/internal/gm/conversation-compaction")
    ConversationCompactionResponse conversationCompaction(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody ConversationCompactionRequest request) {
        requestGuard.internal(token);
        require(request);
        String conversation = write(request.conversation());
        return adapter.complete(request.soloPlayerId(), "conversation-compaction:" + request.sourceStart() + ":" + request.sourceEnd(),
                "ROLE=CONVERSATION_COMPACTION\nSOURCE_START=" + request.sourceStart()
                        + "\nSOURCE_END=" + request.sourceEnd() + "\nEXPECTED_ADVENTURE_VERSION=" + request.expectedAdventureVersion()
                        + "\nCONFIRMED_CONVERSATION=" + conversation
                        + "\nCONFIRMED_RUNTIME_FACTS=" + write(request.runtimeFacts())
                        + "\nTASK=Write one concise Korean summary across the entire confirmed conversation range, preserving its meaning, including character speech, commitments, scene flow, established consequences, unresolved choices, and current goals. You may rewrite and compress the wording. Do not add, infer, or alter facts; do not treat HP, resources, location, or combat state as authoritative."
                        + "\nSOURCE_REFERENCE_RULE=Keep sourceStart, sourceEnd, and expectedAdventureVersion unchanged. The server binds this summary to the exact confirmed source range and version. Do not emit per-entry sequence, speaker, or quotation fields. Keep summary at most 80 percent of source content length."
                        + "\nLONG_TERM_FACT_RULE=longTermFacts is optional. Include only a confirmed Runtime Fact from CONFIRMED_RUNTIME_FACTS that represents an established EVENT, RELATIONSHIP, GOAL, or THREAT with ongoing relevance. Each item must contain factId, establishedTurnId, kind, relevance, and playerVisible. Never create a record for simple dialogue or copy character sheets, HP, resources, location, or combat state."
                        + "\nOUTPUT_CONTRACT=Return exactly one JSON object with sourceStart, sourceEnd, expectedAdventureVersion, summary, and optional longTermFacts [{factId,establishedTurnId,kind,relevance,playerVisible}]. summary must be generated prose grounded only in the supplied confirmed range. Do not use markdown.",
                json -> parseConversationCompaction(json, request));
    }

    private ScenarioCompilationResponse parseCompilation(String json) {
        JsonNode root = readObject(json);
        String status = required(root, "status").toUpperCase(java.util.Locale.ROOT);
        if (!status.equals("READY") && !status.equals("BLOCKED")) {
            throw new IllegalArgumentException("invalid scenario compilation status");
        }
        Map<String, Object> model = root.path("scenarioModel").isObject()
                ? mapper.convertValue(root.path("scenarioModel"), new TypeReference<Map<String, Object>>() { })
                : Map.of();
        if (status.equals("READY") && model.isEmpty()) {
            throw new IllegalArgumentException("READY scenario compilation requires a scenario model");
        }
        List<String> diagnostics = root.has("diagnostics")
                ? mapper.convertValue(root.path("diagnostics"), mapper.getTypeFactory()
                        .constructCollectionType(List.class, String.class)) : List.of();
        return new ScenarioCompilationResponse(status,
                model, diagnostics);
    }

    private ScenarioLookupResponse parseLookup(String json) {
        JsonNode root = readObject(json);
        List<String> ids = root.has("supportingElementIds")
                ? mapper.convertValue(root.path("supportingElementIds"), mapper.getTypeFactory()
                        .constructCollectionType(List.class, String.class))
                : List.of();
        String status = required(root, "status").toUpperCase(java.util.Locale.ROOT);
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

    private ConversationCompactionResponse parseConversationCompaction(String json, ConversationCompactionRequest request) {
        JsonNode root = readObject(json);
        long sourceStart = root.path("sourceStart").asLong(Long.MIN_VALUE);
        long sourceEnd = root.path("sourceEnd").asLong(Long.MIN_VALUE);
        long version = root.path("expectedAdventureVersion").asLong(Long.MIN_VALUE);
        if (sourceStart != request.sourceStart() || sourceEnd != request.sourceEnd() || version != request.expectedAdventureVersion()) {
            throw new IllegalArgumentException("conversation compaction response source does not match request");
        }
        String summary = required(root, "summary");
        long sourceLength = request.conversation().stream().mapToLong(entry -> entry.content().length()).sum();
        if (summary.length() * 5 > sourceLength * 4) throw new IllegalArgumentException("conversation summary must be meaningfully shorter");
        List<LongTermFactCandidate> longTermFacts = new ArrayList<>();
        JsonNode factNodes = root.path("longTermFacts");
        if (factNodes.isArray()) {
            for (JsonNode fact : factNodes) {
                try {
                    if (!fact.isObject()) continue;
                    java.util.UUID factId = java.util.UUID.fromString(required(fact, "factId"));
                    java.util.UUID establishedTurnId = java.util.UUID.fromString(required(fact, "establishedTurnId"));
                    String kind = required(fact, "kind").toUpperCase(java.util.Locale.ROOT);
                    if (!List.of("EVENT", "RELATIONSHIP", "GOAL", "THREAT").contains(kind)) continue;
                    if (!fact.has("playerVisible") || !fact.path("playerVisible").isBoolean()) continue;
                    boolean confirmed = request.runtimeFacts().stream().anyMatch(runtimeFact -> runtimeFact.factId().equals(factId)
                            && runtimeFact.establishedTurnId().equals(establishedTurnId));
                    if (!confirmed) continue;
                    longTermFacts.add(new LongTermFactCandidate(factId, establishedTurnId, kind,
                            required(fact, "relevance"), fact.path("playerVisible").booleanValue()));
                } catch (IllegalArgumentException malformedOptionalFact) {
                    // A proposed long-term record is optional; malformed proposals do not invalidate source excerpts.
                }
            }
        }
        return new ConversationCompactionResponse(sourceStart, sourceEnd, version, summary, List.copyOf(longTermFacts));
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

    private static List<String> stringList(Object value) {
        if (value == null) return List.of();
        if (!(value instanceof List<?> list) || list.stream().anyMatch(item -> !(item instanceof String))) {
            throw new IllegalArgumentException("runtime GM text entries must be strings");
        }
        return list.stream().map(String.class::cast).toList();
    }

    private static void require(Object request) {
        if (request == null) throw new IllegalArgumentException("typed agent request is required");
    }

    public record ScenarioCompilationRequest(java.util.UUID soloPlayerId, String operationKey, String storybookContext) {
        public ScenarioCompilationRequest {
            soloPlayerId = Objects.requireNonNull(soloPlayerId, "soloPlayerId is required");
            operationKey = required(operationKey, "operationKey");
            storybookContext = required(storybookContext, "storybookContext");
        }
    }

    public record ScenarioLookupRequest(java.util.UUID soloPlayerId, String query, Map<String, Object> lockedScenarioModel) {
        public ScenarioLookupRequest {
            soloPlayerId = Objects.requireNonNull(soloPlayerId, "soloPlayerId is required");
            query = required(query, "query");
            lockedScenarioModel = Map.copyOf(Objects.requireNonNull(lockedScenarioModel, "lockedScenarioModel is required"));
        }
    }

    public record ConversationCompactionRequest(java.util.UUID soloPlayerId, long sourceStart, long sourceEnd, long expectedAdventureVersion,
                                                 List<ConversationEntry> conversation, List<RuntimeFactReference> runtimeFacts) {
        public ConversationCompactionRequest(long sourceStart, long sourceEnd, long expectedAdventureVersion,
                List<ConversationEntry> conversation) {
            this(new java.util.UUID(0L, 0L), sourceStart, sourceEnd, expectedAdventureVersion, conversation, List.of());
        }
        public ConversationCompactionRequest(java.util.UUID soloPlayerId, long sourceStart, long sourceEnd, long expectedAdventureVersion, List<ConversationEntry> conversation) { this(soloPlayerId, sourceStart, sourceEnd, expectedAdventureVersion, conversation, List.of()); }
        public ConversationCompactionRequest {
            soloPlayerId = Objects.requireNonNull(soloPlayerId, "soloPlayerId is required");
            if (sourceStart < 0 || sourceEnd < sourceStart || expectedAdventureVersion < 0) throw new IllegalArgumentException("invalid conversation range");
            conversation = List.copyOf(Objects.requireNonNull(conversation, "conversation is required"));
            runtimeFacts = List.copyOf(Objects.requireNonNull(runtimeFacts, "runtime facts are required"));
            if (conversation.isEmpty()) throw new IllegalArgumentException("conversation is required");
            long expectedCount = sourceEnd - sourceStart + 1;
            if (expectedCount <= 0 || conversation.size() != expectedCount) throw new IllegalArgumentException("conversation must cover the requested range");
            for (int i = 0; i < conversation.size(); i++) {
                if (conversation.get(i).sequence() != sourceStart + i) throw new IllegalArgumentException("conversation must cover the requested range");
            }
        }
    }
    public record RuntimeFactReference(java.util.UUID factId, java.util.UUID establishedTurnId, String content, String subject) {
        public RuntimeFactReference(java.util.UUID factId, java.util.UUID establishedTurnId, String content) { this(factId, establishedTurnId, content, ""); }
        public RuntimeFactReference { factId = Objects.requireNonNull(factId, "runtime fact id is required"); establishedTurnId = Objects.requireNonNull(establishedTurnId, "runtime fact turn is required"); content = required(content, "runtime fact content"); subject = subject == null ? "" : subject.trim(); }
    }
    public record ConversationEntry(long sequence, String speaker, String content) {
        public ConversationEntry { if (sequence < 0) throw new IllegalArgumentException("sequence is invalid"); speaker = required(speaker, "speaker"); content = required(content, "content"); }
    }
    public record LongTermFactCandidate(java.util.UUID factId, java.util.UUID establishedTurnId, String kind, String relevance, boolean playerVisible) { }
    public record ConversationCompactionResponse(long sourceStart, long sourceEnd, long expectedAdventureVersion,
                                                 String summary, List<LongTermFactCandidate> longTermFacts) {
        public ConversationCompactionResponse(long sourceStart, long sourceEnd, long expectedAdventureVersion, String summary) { this(sourceStart, sourceEnd, expectedAdventureVersion, summary, List.of()); }
        public ConversationCompactionResponse { summary = required(summary, "summary"); longTermFacts = List.copyOf(longTermFacts); }
    }

    public record RuntimeEndpointRequest(java.util.UUID endpointId, String provider, String model, String reasoning) { }
    public record RuntimeEndpointResponse(java.util.UUID endpointId, String endpointVersion,
                                          String provider, String model, String reasoning) { }

    public record RuntimeTurnRequest(java.util.UUID soloPlayerId, String operationKey, String action,
                                     java.util.UUID endpointId, String provider, String model, String reasoning,
                                     java.util.UUID effectiveEndpointId, String effectiveEndpointVersion,
                                     String effectiveProvider, String effectiveModel, String prompt) {
        public RuntimeTurnRequest(java.util.UUID soloPlayerId, String operationKey, String action,
                java.util.List<java.util.Map<String, Object>> ignored) {
            this(soloPlayerId, operationKey, action, null, "codex-cli", "gpt-5.6-luna", "medium",
                    java.util.UUID.nameUUIDFromBytes("codex-cli/gpt-5.6-luna".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    java.time.Instant.EPOCH.toString(), "codex-cli", "gpt-5.6-luna", "ROLE=RUNTIME_GM");
        }
        public RuntimeTurnRequest(java.util.UUID soloPlayerId, String operationKey, String action,
                java.util.List<java.util.Map<String, Object>> ignored, java.util.Map<String, Object> ignoredContext) {
            this(soloPlayerId, operationKey, action, ignored);
        }
        public RuntimeTurnRequest(java.util.UUID soloPlayerId, String operationKey, String action,
                java.util.UUID endpointId, String provider, String model, String reasoning,
                java.util.List<java.util.Map<String, Object>> ignored, java.util.Map<String, Object> ignoredContext) {
            this(soloPlayerId, operationKey, action, endpointId, provider, model, reasoning,
                    endpointId == null ? java.util.UUID.nameUUIDFromBytes((provider + "/" + model).getBytes(java.nio.charset.StandardCharsets.UTF_8)) : endpointId,
                    java.time.Instant.EPOCH.toString(), provider, model, "ROLE=RUNTIME_GM");
        }
        public RuntimeTurnRequest {
            soloPlayerId = Objects.requireNonNull(soloPlayerId, "soloPlayerId is required");
            operationKey = required(operationKey, "operationKey");
            action = required(action, "action");
            provider = required(provider, "provider");
            model = required(model, "model");
            reasoning = required(reasoning, "reasoning");
            effectiveEndpointId = Objects.requireNonNull(effectiveEndpointId, "effectiveEndpointId is required");
            effectiveEndpointVersion = required(effectiveEndpointVersion, "effectiveEndpointVersion");
            effectiveProvider = required(effectiveProvider, "effectiveProvider");
            effectiveModel = required(effectiveModel, "effectiveModel");
            prompt = required(prompt, "prompt");
        }
    }

    public record NarrationSafetyRequest(java.util.UUID soloPlayerId, String narration, List<String> disclosedFactIds) {
        public NarrationSafetyRequest {
            soloPlayerId = Objects.requireNonNull(soloPlayerId, "soloPlayerId is required");
            narration = required(narration, "narration");
            disclosedFactIds = List.copyOf(Objects.requireNonNull(disclosedFactIds, "disclosedFactIds is required"));
        }
    }

    public record ScenarioCompilationResponse(String status, Map<String, Object> scenarioModel, List<String> diagnostics) {
        public ScenarioCompilationResponse {
            scenarioModel = scenarioModel == null ? Map.of() : Map.copyOf(scenarioModel);
            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        }
    }
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
