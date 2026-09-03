package com.dndmaster.aigamemaster.api;

import com.dndmaster.aigamemaster.infrastructure.ai.SpringAiChatAdapter;
import com.dndmaster.aigamemaster.infrastructure.ai.CodexCliStoryPlanAdapter;
import com.dndmaster.aigamemaster.infrastructure.ai.AiCallObservability;
import com.dndmaster.aigamemaster.infrastructure.ai.CodexAppServerClient;
import com.dndmaster.aigamemaster.application.endpoint.AgentEndpoint;
import com.dndmaster.aigamemaster.application.endpoint.AgentEndpointRegistry;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestHeader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Generates a source-aware outline. JSON is validated before crossing the service boundary. */
@RestController("aiAdventureStoryPlanController")
public final class AdventureStoryPlanController {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdventureStoryPlanController.class);
    private static final int STORY_PLAN_SCHEMA_VERSION = 2;
    private static final Pattern RESOLUTION_FIELD = Pattern.compile("(?im)^(\\s*-\\s*DC\\s+or\\s+dice:\\s*)(.*)$");
    private static final Pattern DC_VALUE = Pattern.compile("(?i)\\bDC\\s*(\\d+)\\b");
    private final SpringAiChatAdapter adapter; private final ObjectMapper mapper; private final AgentEndpointRegistry endpointRegistry;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final URI ollamaBaseUrl; private final String ollamaModel;
    private final URI ruleKnowledgeBaseUrl;
    private final String codexExecutable; private final java.nio.file.Path codexWorkDirectory; private final Duration codexTimeout;
    private final String codexReasoning;
    private final ApiRequestGuard requestGuard;
    public AdventureStoryPlanController(SpringAiChatAdapter adapter, ObjectMapper mapper, AgentEndpointRegistry endpointRegistry,
            @Value("${local-ai.ollama.base-url:http://127.0.0.1:11434}") String ollamaBaseUrl,
            @Value("${local-ai.ollama.chat-model:qwen3:8b}") String ollamaModel,
            @Value("${ai.codex.executable:codex}") String codexExecutable,
            @Value("${ai.codex.work-directory:.}") String codexWorkDirectory,
            @Value("${ai.codex.timeout:PT10M}") Duration codexTimeout,
            @Value("${ai.codex.reasoning:${GM_REASONING:medium}}") String codexReasoning,
            @Value("${ai-game-master.integration.internal-token:${INTERNAL_SERVICE_TOKEN:}}") String internalToken,
            @Value("${rule-knowledge.base-url:${RULE_KNOWLEDGE_BASE_URL:http://127.0.0.1:8080/}}") String ruleKnowledgeBaseUrl) {
        this(adapter, mapper, endpointRegistry, ollamaBaseUrl, ollamaModel, codexExecutable, codexWorkDirectory, codexTimeout,
                codexReasoning, new ApiRequestGuard(internalToken), ruleKnowledgeBaseUrl);
    }
    public AdventureStoryPlanController(SpringAiChatAdapter adapter, ObjectMapper mapper, AgentEndpointRegistry endpointRegistry,
            String ollamaBaseUrl, String ollamaModel, String codexExecutable, String codexWorkDirectory, Duration codexTimeout,
            ApiRequestGuard requestGuard) {
        this(adapter, mapper, endpointRegistry, ollamaBaseUrl, ollamaModel, codexExecutable, codexWorkDirectory, codexTimeout, "medium", requestGuard);
    }
    public AdventureStoryPlanController(SpringAiChatAdapter adapter, ObjectMapper mapper, AgentEndpointRegistry endpointRegistry,
            String ollamaBaseUrl, String ollamaModel, String codexExecutable, String codexWorkDirectory, Duration codexTimeout,
            String codexReasoning, ApiRequestGuard requestGuard) {
        this(adapter, mapper, endpointRegistry, ollamaBaseUrl, ollamaModel, codexExecutable, codexWorkDirectory, codexTimeout,
                codexReasoning, requestGuard, "http://127.0.0.1:8080/");
    }
    public AdventureStoryPlanController(SpringAiChatAdapter adapter, ObjectMapper mapper, AgentEndpointRegistry endpointRegistry,
            String ollamaBaseUrl, String ollamaModel, String codexExecutable, String codexWorkDirectory, Duration codexTimeout,
            String codexReasoning, ApiRequestGuard requestGuard, String ruleKnowledgeBaseUrl) {
        this.adapter = adapter; this.mapper = mapper; this.endpointRegistry = endpointRegistry; this.ollamaBaseUrl = URI.create(ollamaBaseUrl); this.ollamaModel = ollamaModel;
        this.ruleKnowledgeBaseUrl = URI.create(ruleKnowledgeBaseUrl);
        this.codexExecutable = codexExecutable; this.codexWorkDirectory = java.nio.file.Path.of(codexWorkDirectory); this.codexTimeout = codexTimeout; this.codexReasoning = codexReasoning;
        this.requestGuard = requestGuard;
    }
    @PostMapping("/internal/v1/gm/adventure-story-plan")
    Response generate(@RequestHeader(value = "X-Internal-Token", required = false) String internalToken, @RequestBody Request request) {
        requestGuard.internal(internalToken);
        AgentEndpoint endpoint = endpointRegistry.active();
        LOGGER.info("story_plan_generation_started operationId={} provider={} model={} reasoning={}", AiCallObservability.safe(request.operationId()), endpoint.provider(), AiCallObservability.safe(endpoint.model()), AiCallObservability.safe(codexReasoning));
        Configuration configuration = request.configuration() == null ? Configuration.defaults() : request.configuration();
        String availableMaps = request.maps().isEmpty() ? "(no maps supplied)" : request.maps().stream()
                .map(map -> "- mapDefinitionId=" + map.mapDefinitionId() + ", assetId=" + map.assetId()
                        + ", assetLocator=" + map.assetLocator() + ", sourceLocator=" + map.sourceLocator()
                        + ", confidence=" + map.confidence() + ", safetyStatus=" + map.safetyStatus()
                        + ", mapContext=" + map.context())
                .collect(java.util.stream.Collectors.joining("\n"));
        String prompt = structuredStoryPlanPrompt(request, configuration, availableMaps);
        try {
            String canonicalJson = complete(endpoint, request.operationId(), prompt, configuration,
                    retrievalTools(request.retrievalContext(), request.citations()));
            List<Stage> stages = parseJson(canonicalJson, configuration, request.citations());
            return new Response(stages, renderMarkdown(stages));
        } catch (CandidateResponseValidationException invalidCandidate) {
            throw invalidCandidate;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("story plan generation interrupted via " + endpoint.provider(), e);
        } catch (Exception e) {
            throw new IllegalStateException("story plan generation failed via " + endpoint.provider() + ": " + rootMessage(e), e);
        }
    }

    @ExceptionHandler(CandidateResponseValidationException.class)
    ResponseEntity<CandidateValidationError> candidateValidation(CandidateResponseValidationException failure) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new CandidateValidationError(failure.violations(), failure.structuredViolations(), failure.rejectedCandidate()));
    }

    @PostMapping("/internal/v1/gm/adventure-story-plan/repair")
    Response repair(@RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @RequestBody RepairRequest request) {
        requestGuard.internal(internalToken);
        if (request.previousCandidate() == null || request.previousCandidate().isNull()
                || !request.previousCandidate().isObject()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "previous full projection candidate is required");
        }
        if (request.violations().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "structured projection violations are required");
        }
        if (request.repairScope() == null || request.repairScope().allowedPaths().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "deterministic repair scope is required");
        }
        AgentEndpoint endpoint = endpointRegistry.active();
        Configuration configuration = request.configuration() == null ? Configuration.defaults() : request.configuration();
        try {
            String repaired = complete(endpoint, request.operationId() + "-projection-repair",
                    repairPrompt(request, configuration), configuration,
                    retrievalTools(request.retrievalContext(), request.citations()));
            return new Response(parseJson(repaired, configuration, request.citations()));
        } catch (CandidateResponseValidationException invalidCandidate) {
            throw invalidCandidate;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("story plan projection repair interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("story plan projection repair failed: " + rootMessage(e), e);
        }
    }

    private String structuredStoryPlanPrompt(Request request, Configuration configuration, String availableMaps) {
        return """
                You are the canonical Story Plan generator. Return ONLY one JSON object; do not return Markdown.
                The JSON response is the authoritative Story Plan model. Java will parse it, validate schema,
                references, citations, maps, stage counts, and grounding before publishing it. Never invent IDs.
                Root shape: {"stages":[...]}. Generate %s-%s stages and exactly %s distinct ending IDs: ending-1 through ending-%s.
                Every stage requires position, title, goal, conflict, transitionCondition, endingIds, evidence, schemaVersion,
                combatRequirement, combatSkeleton, sourceFactClaims, and tacticalPreparationRequirement.
                Use schemaVersion 2. Evidence items contain only citationKey copied verbatim from the supplied registry.
                Every stage with supplied citations must contain at least one registered evidence citationKey.
                sourceFactClaims are limited to combatSkeleton paths and SOURCE claims must cite the same-stage evidence.
                A combat participant must be supported by an excerpt returned by RAG; otherwise omit the commitment.
                Never return a committed participant with an empty citationKeys array. Every committed participant must
                have role exactly ENEMY or BOSS, a non-empty participantId, and at least one citationKey copied from
                the same stage evidence. If no same-stage citation supports the participant, remove the participant and
                downgrade the stage to POSSIBLE or NONE.
                Preserve explicit failure/fail-forward consequences in failureCondition. Do not create claims for narrative fields.
                Every conditional event, hidden-information trigger, secret, clue reveal, or rules check MUST state an explicit
                success outcome and an explicit failure or fail-forward consequence in the same stage prose. Do not encode a
                one-sided conditional branch; use source-grounded outcomes and do not invent an outcome absent from the evidence.
                Map IDs, assets, and locators must be copied from one AVAILABLE MAPS entry. Dungeon stages with supplied maps require REQUIRED map usage.
                All player-facing prose must be natural Korean. Keep arrays explicit, including empty arrays.
                configuration=%s
                RAG_RETRIEVAL:
                Use the dynamic tools search_story_sources and search_rule_evidence to retrieve authoritative excerpts.
                Query the tools before making any source-grounded claim. The tools return citationKey and excerpt values;
                use only citationKey values returned by those tools. Do not rely on preloaded source text.
                Registered citation metadata (keys only)=%s
                maps=%s
                previousViolations=%s
                previousCandidate=%s

                AVAILABLE MAPS (authoritative):
                %s
                """.formatted(configuration.minimumStages(), configuration.maximumStages(), configuration.endingCount(),
                configuration.endingCount(), configuration, citationMetadata(request.citations()), promptMaps(request.maps()), request.violations(),
                request.previousCandidate(), availableMaps);
    }

    /** Stable player-facing view derived solely from the canonical structured candidate. */
    static String renderMarkdown(List<Stage> stages) {
        StringBuilder markdown = new StringBuilder("# Adventure Plan\n\n");
        for (Stage stage : stages == null ? List.<Stage>of() : stages) {
            markdown.append("## Stage ").append(stage.position()).append(": ").append(stage.title()).append("\n")
                    .append("- Type: ").append(stage.stageType()).append("\n")
                    .append("- Location: ").append(stage.location()).append("\n")
                    .append("- Goal: ").append(stage.goal()).append("\n")
                    .append("- Conflict: ").append(stage.conflict()).append("\n")
                    .append("- Entry condition: ").append(stage.transitionCondition()).append("\n")
                    .append("- Clear condition: ").append(stage.clearCondition()).append("\n")
                    .append("- Failure condition: ").append(stage.failureCondition()).append("\n")
                    .append("- Enemies: ").append(String.join(", ", stage.enemies())).append("\n")
                    .append("- Boss: ").append(stage.boss()).append("\n")
                    .append("- Rewards: ").append(String.join(", ", stage.rewards())).append("\n")
                    .append("- Ending IDs: ").append(String.join(", ", stage.endingIds())).append("\n")
                    ;
            if (completeCombatSkeleton(stage.combatSkeleton())) {
                markdown.append("- Combat trigger: ").append(stage.combatSkeleton().startTrigger()).append("\n")
                        .append("- Combat success outcome: ").append(stage.combatSkeleton().successOutcome()).append("\n")
                        .append("- Combat failure/fail-forward outcome: ").append(stage.combatSkeleton().failureOutcome()).append("\n");
            }
            markdown
                    .append("- Source citations: ").append(stage.evidence().stream()
                            .map(CitationProjection::citationKey).sorted().collect(java.util.stream.Collectors.joining(", ")))
                    .append("\n\n");
        }
        return markdown.toString();
    }

    private static boolean completeCombatSkeleton(CombatSkeletonProjection skeleton) {
        return skeleton != null && !skeleton.objective().isBlank() && !skeleton.startTrigger().isBlank()
                && !skeleton.participants().isEmpty() && !skeleton.successOutcome().isBlank()
                && !skeleton.failureOutcome().isBlank();
    }

    private String complete(AgentEndpoint endpoint, String operationId, String prompt, Configuration configuration) throws IOException, InterruptedException {
        return complete(endpoint, operationId, prompt, configuration, List.of());
    }

    private String complete(AgentEndpoint endpoint, String operationId, String prompt, Configuration configuration,
            List<CodexAppServerClient.DynamicTool> dynamicTools) throws IOException, InterruptedException {
        long startedAt = System.nanoTime();
        String phase = phase(operationId);
        String model = endpoint.model().isBlank() ? ollamaModel : endpoint.model();
        LOGGER.info("story_plan_stage_started stage={} operationId={} provider={} model={} reasoning={} promptChars={} estimatedPromptTokens={}", phase, AiCallObservability.safe(operationId), endpoint.provider(), AiCallObservability.safe(model), AiCallObservability.safe(codexReasoning), prompt.length(), AiCallObservability.estimatedTokens(prompt.length()));
        String result = null;
        boolean turnCompletedReceived = false;
        boolean timeout = false;
        try {
            if (endpoint.provider() == AgentEndpoint.Provider.CODEX_CLI) {
                result = new CodexCliStoryPlanAdapter(codexExecutable, model, codexWorkDirectory, codexTimeout, codexReasoning)
                        .complete(operationId, prompt, dynamicTools);
                return result;
            }
            URI baseUrl = endpoint.provider() == AgentEndpoint.Provider.OLLAMA ? endpoint.baseUrl() : ollamaBaseUrl;
            int outputTokens = Math.max(4096, configuration.maximumStages() * 900);
            String body = mapper.writeValueAsString(Map.of("model", model, "prompt", prompt,
                    "stream", false, "think", false, "options", Map.of("num_predict", outputTokens)));
            HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder(baseUrl.resolve("/api/generate"))
                    .timeout(Duration.ofMinutes(10)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("Ollama returned HTTP " + response.statusCode());
            result = mapper.readTree(response.body()).path("response").asText();
            return result;
        } catch (RuntimeException failure) {
            timeout = failure instanceof com.dndmaster.aigamemaster.infrastructure.ai.ProviderTimeoutException;
            throw failure;
        } finally {
            turnCompletedReceived = result != null;
            LOGGER.info("story_plan_stage_completed stage={} operationId={} provider={} model={} reasoning={} durationMs={} promptChars={} estimatedPromptTokens={} responseChars={} turnId={} turnCompletedReceived={} timeout={}", phase, AiCallObservability.safe(operationId), endpoint.provider(), AiCallObservability.safe(model), AiCallObservability.safe(codexReasoning), java.time.Duration.ofNanos(System.nanoTime() - startedAt).toMillis(), prompt.length(), AiCallObservability.estimatedTokens(prompt.length()), result == null ? 0 : result.length(), "provider-managed", turnCompletedReceived, timeout);
            LOGGER.info("story_plan_trace operationId={} phase={} promptChars={} responseChars={} dynamicTools={}",
                    AiCallObservability.safe(operationId), phase, prompt.length(), result == null ? 0 : result.length(), dynamicTools.stream().map(CodexAppServerClient.DynamicTool::name).toList());
        }
    }

    private List<CodexAppServerClient.DynamicTool> retrievalTools(
            RetrievalContext context, List<SourceCitation> citations) {
        if (context == null || context.ownerId() == null || context.documents().isEmpty()) return List.of();
        return List.of(
                new CodexAppServerClient.DynamicTool(
                        "search_story_sources",
                        "Search the indexed published STORYBOOK evidence for a focused adventure fact.",
                        retrievalInputSchema(), arguments -> searchStorySources(context, citations, arguments)),
                new CodexAppServerClient.DynamicTool(
                        "search_rule_evidence",
                        "Search the indexed published RULEBOOK evidence for a focused game-rule fact.",
                        retrievalInputSchema(), arguments -> searchRuleEvidence(context, citations, arguments)));
    }

    private ObjectNode retrievalInputSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.putObject("properties").putObject("query").put("type", "string");
        schema.with("properties").putObject("limit").put("type", "integer").put("minimum", 1).put("maximum", 8);
        schema.putArray("required").add("query");
        schema.put("additionalProperties", false);
        return schema;
    }

    private String searchStorySources(RetrievalContext context, List<SourceCitation> citations, JsonNode arguments) {
        ObjectNode body = mapper.createObjectNode();
        body.put("ownerId", context.ownerId().toString());
        ArrayNode documents = body.putArray("documents");
        context.documents().stream().filter(document -> "STORYBOOK".equalsIgnoreCase(document.documentType()))
                .distinct().forEach(document -> documents.addObject()
                        .put("documentId", document.documentId().toString())
                        .put("extractionVersion", document.extractionVersion()));
        body.putArray("activeLocators");
        body.put("situation", query(arguments));
        body.put("limit", limit(arguments));
        return searchEvidence("internal/v1/story-sources/search", body, citations, "STORYBOOK", "knowledgeDocumentId");
    }

    private String searchRuleEvidence(RetrievalContext context, List<SourceCitation> citations, JsonNode arguments) {
        ObjectNode body = mapper.createObjectNode();
        body.put("ownerId", context.ownerId().toString());
        ArrayNode rulebooks = body.putArray("rulebookIds");
        context.documents().stream().filter(document -> "RULEBOOK".equalsIgnoreCase(document.documentType()))
                .map(RetrievalDocument::documentId).distinct().forEach(id -> rulebooks.add(id.toString()));
        body.put("situation", query(arguments));
        body.put("queryIntent", "MIXED");
        body.put("limit", limit(arguments));
        return searchEvidence("internal/v1/rule-evidence/search", body, citations, "RULEBOOK", "rulebookId");
    }

    private String searchEvidence(String path, ObjectNode body, List<SourceCitation> citations,
            String documentType, String documentIdField) {
        try {
            HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder(ruleKnowledgeBaseUrl.resolve(path))
                    .timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + body.path("ownerId").asText())
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("RAG search failed with HTTP " + response.statusCode());
            }
            JsonNode root = mapper.readTree(response.body());
            ArrayNode result = mapper.createArrayNode();
            root.path("evidence").forEach(item -> {
                String documentId = item.path(documentIdField).asText("");
                long extractionVersion = item.path("extractionVersion").asLong(0);
                String locator = item.path("locator").asText("");
                String citationKey = citations.stream()
                        .filter(citation -> documentType.equalsIgnoreCase(citation.documentType()))
                        .filter(citation -> documentId.equals(citation.documentId()))
                        .filter(citation -> extractionVersion == citation.extractionVersion())
                        .filter(citation -> locator.equals(citation.locator()))
                        .map(SourceCitation::citationKey).filter(key -> key != null && !key.isBlank()).findFirst().orElse("");
                if (citationKey.isBlank()) return;
                result.addObject().put("citationKey", citationKey)
                        .put("documentType", documentType)
                        .put("documentId", documentId)
                        .put("extractionVersion", extractionVersion)
                        .put("locator", locator)
                        .put("excerpt", item.path("excerpt").asText(""));
            });
            ObjectNode output = mapper.createObjectNode();
            output.set("evidence", result);
            return output.toString();
        } catch (IOException exception) {
            throw new IllegalStateException("RAG search response could not be read", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("RAG search interrupted", exception);
        }
    }

    private static String query(JsonNode arguments) {
        String query = arguments == null ? "" : arguments.path("query").asText("").trim();
        if (query.isBlank()) throw new IllegalArgumentException("RAG query must not be blank");
        return query;
    }

    private static int limit(JsonNode arguments) {
        return Math.max(1, Math.min(8, arguments == null ? 5 : arguments.path("limit").asInt(5)));
    }

    private String citationMetadata(List<SourceCitation> citations) {
        return (citations == null ? List.<SourceCitation>of() : citations).stream()
                .map(citation -> "citationKey=" + citation.citationKey() + ", documentType=" + citation.documentType()
                        + ", documentId=" + citation.documentId() + ", extractionVersion=" + citation.extractionVersion()
                        + ", locator=" + citation.locator())
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private String promptMaps(List<MapContext> maps) {
        return (maps == null ? List.<MapContext>of() : maps).stream()
                .map(map -> "mapDefinitionId=" + map.mapDefinitionId() + ", assetId=" + map.assetId()
                        + ", assetLocator=" + map.assetLocator() + ", sourceLocator=" + map.sourceLocator()
                        + ", confidence=" + map.confidence() + ", safetyStatus=" + map.safetyStatus()
                        + ", mapContext=" + map.context())
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private String compactCandidateForPrompt(String candidate) {
        if (candidate == null || candidate.isBlank()) return "";
        try {
            JsonNode root = mapper.readTree(candidate);
            root.path("stages").forEach(stage -> {
                if (!stage.isObject() || !stage.path("evidence").isArray()) return;
                ArrayNode evidence = mapper.createArrayNode();
                stage.path("evidence").forEach(item -> {
                    ObjectNode keyOnly = mapper.createObjectNode();
                    keyOnly.put("citationKey", item.path("citationKey").asText(""));
                    evidence.add(keyOnly);
                });
                ((ObjectNode) stage).set("evidence", evidence);
            });
            return root.toString();
        } catch (IOException exception) {
            return "[previous candidate unavailable for prompt]";
        }
    }

    private static String phase(String operationId) {
        if (operationId.endsWith("-projection-repair")) return "story-plan-projection-repair";
        if (operationId.endsWith("-verification")) return "story-plan-verification";
        if (operationId.endsWith("-execution-projection")) return "story-plan-execution-projection";
        return "story-plan-generation";
    }

    static String normalizeResolutionValues(String markdown, List<SourceCitation> citations) {
        if (markdown == null || markdown.isBlank()) return markdown;
        Set<String> evidencedDcs = new LinkedHashSet<>();
        for (SourceCitation citation : citations == null ? List.<SourceCitation>of() : citations) {
            if (citation == null) continue;
            Matcher matcher = DC_VALUE.matcher(citation.quote() == null ? "" : citation.quote());
            while (matcher.find()) evidencedDcs.add(matcher.group(1));
        }
        Matcher fields = RESOLUTION_FIELD.matcher(markdown);
        StringBuffer normalized = new StringBuffer();
        while (fields.find()) {
            String value = fields.group(2);
            Matcher dc = DC_VALUE.matcher(value);
            if (dc.find() && !evidencedDcs.contains(dc.group(1))) {
                String replacement = evidencedDcs.size() == 1
                        ? "DC " + evidencedDcs.iterator().next()
                        : "GM adjudication";
                fields.appendReplacement(normalized, Matcher.quoteReplacement(fields.group(1) + replacement));
            } else {
                fields.appendReplacement(normalized, Matcher.quoteReplacement(fields.group(0)));
            }
        }
        fields.appendTail(normalized);
        return normalized.toString();
    }

    private String projectionPrompt(Request request, Configuration configuration, String generatedMarkdown) {
        return """
                You are the execution projection agent. Read the verified Markdown plan and convert it into the smallest usable execution outline.
                Return ONLY JSON with a stages array. Do not rewrite the narrative or invent facts.
                Each stage MUST include these required fields: position (integer), title, goal, conflict, transitionCondition, endingIds (a non-empty array of strings), evidence (a non-empty array when supplied citations are available), schemaVersion (2), combatRequirement (NONE, POSSIBLE, or REQUIRED), combatSkeleton, sourceFactClaims, and tacticalPreparationRequirement (NOT_REQUIRED or REQUIRED).
                The root object MUST contain stages (an array). Optional fields may be omitted, and additional properties are allowed and ignored.
                JSON shape constraint: {"type":"object","required":["stages"],"properties":{"stages":{"type":"array","items":{"type":"object","required":["position","title","goal","conflict","transitionCondition","endingIds","evidence","schemaVersion","combatRequirement","combatSkeleton","sourceFactClaims","tacticalPreparationRequirement"],"properties":{"position":{"type":"integer"},"title":{"type":"string"},"goal":{"type":"string"},"conflict":{"type":"string"},"transitionCondition":{"type":"string"},"endingIds":{"type":"array","items":{"type":"string"},"minItems":1},"evidence":{"type":"array","items":{"type":"object","required":["citationKey"]},"minItems":1},"schemaVersion":{"type":"integer","const":2},"combatRequirement":{"type":"string","enum":["NONE","POSSIBLE","REQUIRED"]},"combatSkeleton":{"type":"object"},"sourceFactClaims":{"type":"array"},"tacticalPreparationRequirement":{"type":"string","enum":["NOT_REQUIRED","REQUIRED"]}},"additionalProperties":true}}},"additionalProperties":true}
                The plan configuration requires exactly %s distinct ending IDs. Preserve the explicit ending-1 through ending-%s IDs from the plan; do not omit, merge, rename, or invent ending IDs.
                Include stageType, location, and mapUsage (REQUIRED, OPTIONAL, or NONE) when present. Include mapDefinitionId, mapAssetId, and mapAssetLocator only when mapUsage is REQUIRED; copy all three from the same AVAILABLE MAPS entry. OPTIONAL and NONE may omit them.
                Set combatRequirement to REQUIRED only when the stage has a concrete combat skeleton with at least one sourced participant; use POSSIBLE for a source-supported possibility that is not committed, and NONE for a non-combat stage. For every stage, emit combatSkeleton with objective, startTrigger, participants, successOutcome, failureOutcome, and rewards arrays (empty strings for scalar fields and empty arrays for NONE or POSSIBLE). Every participant must include participantId, role (ENEMY or BOSS), name, minimumCount, maximumCount, and citationKeys. Use sourceFactClaims with exact fieldPath values such as combatSkeleton.participants[0].name or combatSkeleton.rewards[0]. Every sourceFactClaims item MUST be an object with non-empty fieldPath, normalizedClaim, citationKeys, and origin (SOURCE, GENERATED, or UNKNOWN). SOURCE claims require citationKeys; GENERATED and UNKNOWN detail may have an empty citationKeys array. normalizedClaim is the exact short claim supported by the cited excerpt for SOURCE claims, never omit it. combatSkeleton.rewards MUST be an array of the same claim objects (fieldPath, normalizedClaim, citationKeys, origin), never an array of strings. Bind every SOURCE claim to exact citation keys, and every citationKey used by a claim MUST also appear in that same stage's evidence array. Set tacticalPreparationRequirement to REQUIRED only when a REQUIRED combat stage is mapped to an available map; otherwise use NOT_REQUIRED.
                Use only citationKey values returned by RAG; never invent numeric, document-derived, or stage-local citation keys. Do not create sourceFactClaims for goal, conflict, transitionCondition, clearCondition, or any other narrative field: sourceFactClaims are exclusively combat skeleton claims. A combat participant must be grounded by an excerpt returned by RAG, and its citationKeys must point to that same returned result. Keep combatRequirement consistent with the stage: NONE has no combat hints, POSSIBLE has no committed participant, and REQUIRED has a complete sourced skeleton. If a combat claim cannot be grounded by an exact RAG result, omit the combat commitment or regenerate the complete projection rather than guessing.
                Optional fields may be omitted or empty: npcOrClues, enemies, boss, clearCondition, failureCondition, rewards, branchIds, branchTargets, and player spawn fields. When citations are registered, evidence is REQUIRED for every stage: copy at least one exact citationKey returned by RAG. Do not copy document IDs, extraction versions, locators, quotes, or confidence into evidence. When both STORYBOOK and RULEBOOK citations are registered, the complete plan MUST include at least one exact citationKey for each type across its stages. A trigger is represented only by a short reference or lookup key; never copy the full trigger or rule text. Every conditional event, hidden-information trigger, secret, clue reveal, or rules check MUST preserve both an explicit success outcome and an explicit failure or fail-forward consequence from the verified Markdown. Do not encode a one-sided conditional branch, and do not invent either outcome when the source does not support it.
                Arrays may be empty arrays and branchTargets may be an empty object. Never invent a map, trigger, citation, enemy, reward, or ending. Use the ending IDs stated in the plan, or a stable structural ending ID when necessary.
                If the plan is invalid, return the best faithful projection so the application can report the violation. Preserve required burning-web or other hazard failure consequences from the verified Markdown as stage failureCondition text; do not turn them into sourceFactClaims or drop them.
                configuration=%s
                RAG_RETRIEVAL:
                Use the dynamic tools search_story_sources and search_rule_evidence to retrieve authoritative excerpts.
                Query the tools before making any source-grounded claim. The tools return citationKey and excerpt values;
                use only citationKey values returned by those tools. Do not rely on preloaded source text.
                registeredCitationMetadata=%s
                maps=%s
                previousViolations=%s

                GENERATED MARKDOWN:
                %s
                """.formatted(configuration.endingCount(), configuration.endingCount(), configuration,
                citationMetadata(request.citations()), promptMaps(request.maps()), request.violations(), generatedMarkdown);
    }

    private String repairPrompt(RepairRequest request, Configuration configuration) {
        return """
                You are repairing one rejected execution projection. Return the COMPLETE projection JSON object, never a patch.
                Preserve every field exactly unless its JSON path is listed in STRUCTURED VIOLATIONS. Do not add, remove, rename, or
                mutate any unlisted field. Use only citation keys returned by the dynamic RAG tools and the registered map metadata; never invent,
                fuzzy-match, or copy a quote, locator, document ID, map ID, or source that was not returned by RAG. The server will rerun the
                complete schema, citation/map/source, and business-rule validation chain after this response.
                The response root MUST be an object with a stages array and must contain the full candidate, not a JSON patch. For a failure-consequence violation, repair only the affected stage failureCondition and preserve its concrete fail-forward consequence. For citation or participant violations, use only exact registered keys and keep the claim, participant, and same-stage evidence binding consistent.
                Every committed combat participant MUST contain non-empty participantId, role (ENEMY or BOSS), name,
                minimumCount, maximumCount, and citationKeys. citationKeys MUST contain at least one exact key from the
                same stage evidence whose quote supports the participant name. If that cannot be satisfied, remove the
                participant and set combatRequirement to POSSIBLE or NONE. tacticalPreparationRequirement MUST be exactly
                NOT_REQUIRED or REQUIRED; never place a DC, check, prose, or rule text in that field.
                configuration=%s
                structuredViolations=%s
                deterministicRepairScope=%s
                RAG_RETRIEVAL:
                Use the dynamic tools search_story_sources and search_rule_evidence to retrieve only the authoritative
                excerpts needed for the listed violations. The tools return citationKey and excerpt values; use only
                citationKey values returned by those tools. Do not rely on preloaded source text.
                registeredCitationMetadata=%s
                previousFullCandidate=%s
                """.formatted(configuration, request.violations(), request.repairScope(),
                citationMetadata(request.citations()), compactCandidateForPrompt(request.previousCandidate().toString()));
    }

    private String verificationDecisionPrompt(Request request, Configuration configuration, String generatedMarkdown) {
        return """
                You are an independent verifier for a generated tabletop adventure plan.
                Inspect only whether essential information is present and usable against RAG results, registered map metadata, and configuration.
                Do not rewrite, summarize, extract, or normalize the plan. Do not return stages or any other plan data.
                Return ONLY one JSON object with exactly these fields: {"status":"PASS"|"FAIL","violations":["..."]}.
                Use PASS when the plan has a goal, start situation, playable progression, transition or completion conditions, and at least one ending.
                In this contract, endingIds are the canonical ending references. Do not require a separate ending prose section when valid endingIds and a completion condition are present.
                This is a Story Plan outline, not a tactical-scene or resolution-plan contract. Do not infer a missing trigger, DC, check, success result, or failure result from narrative prose in goal, conflict, transitionCondition, clearCondition, or failureCondition. Those details are created and validated later by the runtime tactical-scene and resolution pipelines. Only assess a trigger or check when the plan explicitly supplies a complete structured combat skeleton.
                Check map usage per stage. A stage marked REQUIRED must contain an exact supplied mapDefinitionId, assetId, and assetLocator from the same map entry. OPTIONAL and NONE stages may omit map references. Do not infer that every dungeon or exploration stage requires a map.
                For triggers and checks, first decide whether a stage actually needs one. A stage without hidden information, a conditional event, or a rules check may have no trigger and still PASS.
                When a trigger or check is needed, verify only that its activation condition, check (if any), and resulting outcome are usable, and that explicitly evidenced core triggers or checks were not omitted.
                Every hidden-information trigger, secret, clue reveal, conditional event, or rules check that is present MUST have an explicit success result and an explicit failure or fail-forward consequence. A trigger with only one outcome is unusable and must produce a concise violation naming the affected stage.
                Do not fail for heading names, Markdown formatting, stage count wording, prose style, optional details, or reasonable additions not contradicted by evidence.
                Fail only for missing essential information, an unusable required trigger/check, a missing required map reference, or a clear contradiction with supplied evidence.
                violations must contain concise actionable descriptions. Return an empty array only for PASS.
                configuration=%s
                RAG_RETRIEVAL:
                Use the dynamic tools search_story_sources and search_rule_evidence to retrieve only the authoritative
                excerpts needed to verify source-grounded claims. Do not rely on preloaded source text.
                registeredCitationMetadata=%s
                maps=%s
                generatedMarkdown=
                %s
                """.formatted(configuration, citationMetadata(request.citations()), promptMaps(request.maps()), generatedMarkdown);
    }

    /** Verifies an already generated plan without rewriting it into the execution model. */
    @PostMapping("/internal/v1/gm/adventure-story-plan/verify")
    VerificationResponse verify(@RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @RequestBody VerificationRequest request) {
        requestGuard.internal(internalToken);
        if (request.generatedMarkdown() == null || request.generatedMarkdown().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "generated markdown is required");
        }
        AgentEndpoint endpoint = endpointRegistry.active();
        Configuration configuration = request.configuration() == null ? Configuration.defaults() : request.configuration();
        String prompt = """
                You are an independent verifier for a generated tabletop adventure plan.
                Inspect only whether essential information is present and usable against RAG results, registered map metadata, and configuration.
                Do not rewrite, summarize, extract, or normalize the plan. Do not return stages or any other plan data.
                Return ONLY one JSON object with exactly these fields: {"status":"PASS"|"FAIL","violations":["..."]}.
                Use PASS when the plan has a goal, start situation, playable progression, transition or completion conditions, and at least one ending.
                In this contract, endingIds are the canonical ending references. Do not require a separate ending prose section when valid endingIds and a completion condition are present.
                This is a Story Plan outline, not a tactical-scene or resolution-plan contract. Do not infer a missing trigger, DC, check, success result, or failure result from narrative prose in goal, conflict, transitionCondition, clearCondition, or failureCondition. Those details are created and validated later by the runtime tactical-scene and resolution pipelines. Only assess a trigger or check when the plan explicitly supplies a complete structured combat skeleton.
                Check map usage per stage. A stage marked REQUIRED must have an exact supplied map reference; OPTIONAL and NONE may omit one. Do not require maps solely because a stage is a dungeon or exploration scene.
                A stage without hidden information, a conditional event, or a rules check may have no trigger and still PASS.
                When a trigger or check is needed, verify only that its activation condition, check (if any), and outcome are usable.
                Every hidden-information trigger, secret, clue reveal, conditional event, or rules check that is present MUST have an explicit success result and an explicit failure or fail-forward consequence. A trigger with only one outcome is unusable and must produce a concise violation naming the affected stage.
                Do not fail for heading names, Markdown formatting, stage count wording, prose style, or optional details.
                Fail only for missing essential information, an unusable required trigger/check, a missing required map reference, or a clear contradiction.
                violations must contain concise, actionable Korean or English descriptions. Return an empty array only for PASS.
                configuration=%s
                RAG_RETRIEVAL:
                Use the dynamic tools search_story_sources and search_rule_evidence to retrieve only the authoritative
                excerpts needed to verify source-grounded claims. Do not rely on preloaded source text.
                registeredCitationMetadata=%s
                maps=%s
                generatedMarkdown=
                %s
                """.formatted(configuration, citationMetadata(request.citations()), promptMaps(request.maps()), request.generatedMarkdown());
        try {
            String response = complete(endpoint, request.operationId(), prompt, configuration,
                    retrievalTools(request.retrievalContext(), request.citations()));
            return parseVerificationResponse(response);
        } catch (CandidateResponseValidationException invalidCandidate) {
            throw invalidCandidate;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("adventure story plan verification interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("adventure story plan verification failed: " + rootMessage(e), e);
        }
    }

    private VerificationResponse parseVerificationResponse(String text) {
        try {
            JsonNode root = mapper.readTree(extractObject(text));
            String status = root.path("status").asText("").trim().toUpperCase(java.util.Locale.ROOT);
            if (!status.equals("PASS") && !status.equals("FAIL")) throw new IllegalArgumentException("verification status must be PASS or FAIL");
            JsonNode violationsNode = root.get("violations");
            if (violationsNode == null || !violationsNode.isArray()) throw new IllegalArgumentException("verification violations must be explicit");
            List<String> violations = new ArrayList<>();
            violationsNode.forEach(item -> {
                if (!item.isTextual() || item.asText().isBlank()) throw new IllegalArgumentException("verification violations must contain text");
                violations.add(item.asText().trim());
            });
            if (status.equals("PASS") && !violations.isEmpty()) throw new IllegalArgumentException("PASS verification must have no violations");
            if (status.equals("FAIL") && violations.isEmpty()) throw new IllegalArgumentException("FAIL verification must include violations");
            return new VerificationResponse(status, List.copyOf(violations));
        } catch (RuntimeException | IOException invalid) {
            throw new CandidateResponseValidationException("invalid verification response: " + rootMessage(invalid), invalid);
        }
    }

    private static String sanitizeDiagnostic(String value) {
        if (value == null) return "candidate validation failed";
        String normalized = value.replaceAll("[\\r\\n\\t]+", " ").replaceAll(" +", " ").trim();
        return normalized.length() <= 256 ? normalized : normalized.substring(0, 256) + "...";
    }

    /** Produces one typed, source-grounded tactical scene candidate for a mapped story-plan stage. */
    @PostMapping("/internal/v1/gm/tactical-scene-plan")
    JsonNode generateTacticalScene(@RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
            @RequestBody JsonNode request) {
        requestGuard.internal(internalToken);
        String operationId = request.path("stage").path("position").asText("stage") + "-tactical-" + UUID.randomUUID();
        String prompt = """
                Produce exactly one JSON object for a tactical-scene candidate. Do not include Markdown.
                The response shape is {"stagePosition":number,"scene":{...},"citations":[...]}.
                scene MUST use schemaVersion 1 and status READY. It must contain boundary {minimum:{x,y},maximum:{x,y},forbiddenCoordinates:[]},
                players/allies/npcs/enemies/bosses/interactiveObjects/environments arrays, initialFog {hiddenRegions,grounding}, triggers, outcomes, and transitionIds.
                Every coordinate is normalized from 0 through 1. Every placement/environment must have id, kind, coordinate, and grounding.
                A grounding is either {type:"SOURCE_CITATION",citation:"documentType:documentUuid:extractionVersion:locator",rationale:""} using ONLY a supplied citation,
                or {type:"AI_INFERENCE",citation:"",rationale:"bounded explanation tied to the supplied map or story evidence"}.
                Source citations and supplied map/story evidence take precedence. Use the supplied partyMemberIds exactly for player identities.
                AI_INFERENCE may choose coordinates or bounded placement details only for identities already present in partyMemberIds, the stage, map evidence, or source citations.
                Never invent a named ally, NPC, enemy, boss, interactive object, reward, ending, monster, or map fact not present in them.
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
        } catch (CandidateResponseValidationException invalidCandidate) {
            throw invalidCandidate;
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
            throw new CandidateResponseValidationException("tactical candidate is not valid JSON", e);
        }
    }

    private static String rootMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null) root = root.getCause();
        return root.getMessage() == null || root.getMessage().isBlank() ? root.getClass().getSimpleName() : root.getMessage();
    }
    private List<Stage> parseJson(String text, Configuration configuration) {
        return parseJson(text, configuration, List.of());
    }

    private List<Stage> parseJson(String text, Configuration configuration, List<SourceCitation> authoritativeCitations) {
        try {
            JsonNode root = canonicalizeProjection(mapper.readTree(extractObject(text)), authoritativeCitations, configuration);
            JsonNode stages = root.get("stages");
            if (stages == null || !stages.isArray()) throw new IllegalArgumentException("stages missing");
            List<Stage> result = new ArrayList<>();
            for (int stageIndex = 0; stageIndex < stages.size(); stageIndex++) {
                JsonNode n = stages.get(stageIndex);
                if (!n.isObject()) throw new IllegalArgumentException("stage must be an object");
                JsonNode position = n.get("position");
                if (position == null || !position.isIntegralNumber()) throw new IllegalArgumentException("position missing");
                JsonNode endingNode = n.get("endingIds");
                if (endingNode == null || !endingNode.isArray()) {
                    throw endingIdsViolation(position.intValue(), stageIndex, "", root, "endingIds must be explicit");
                }
                List<String> endings = strings(endingNode, "endingIds");
                if (endings.isEmpty()) {
                    throw endingIdsViolation(position.intValue(), stageIndex, endingNode.toString(), root, "endingIds must not be empty");
                }
                result.add(new Stage(position.intValue(), required(n,"title"), text(n, "stageType", "EVENT"), text(n, "location", required(n, "title")), required(n,"goal"), required(n,"conflict"), required(n,"transitionCondition"), optionalStrings(n.get("npcOrClues")), endings,
                        text(n, "mapDefinitionId", ""), text(n, "mapAssetId", ""), text(n, "mapAssetLocator", ""), optionalStrings(n.get("enemies")), text(n, "boss", ""), text(n, "clearCondition", required(n, "transitionCondition")), text(n, "failureCondition", ""), optionalStrings(n.get("rewards")), optionalStrings(n.get("branchIds")), optionalMaps(n.get("branchTargets")), optionalCitations(n.get("evidence")),
                        text(n, "combatRequirement", "NONE"), parseCombatSkeleton(n.get("combatSkeleton")), parseSourceFactClaims(n.get("sourceFactClaims")),
                        text(n, "tacticalPreparationRequirement", "NOT_REQUIRED"), n.path("schemaVersion").asInt(1)));
            }
            if (result.size() < configuration.minimumStages() || result.size() > configuration.maximumStages()) throw new IllegalArgumentException("invalid stage count");
            if (result.stream().flatMap(s -> s.endingIds().stream()).distinct().count() != configuration.endingCount()) throw new IllegalArgumentException("invalid ending count");
            return List.copyOf(result);
        } catch (CandidateResponseValidationException invalidCandidate) {
            throw invalidCandidate;
        } catch (Exception e) {
            JsonNode rejected = null;
            try { rejected = mapper.readTree(extractObject(text)); }
            catch (Exception ignored) { }
            throw new CandidateResponseValidationException(List.of(rootMessage(e)), e, rejected);
        }
    }

    /**
     * Provider JSON is untrusted. Canonicalize only at the projection boundary so
     * invented provenance or combat facts cannot enter the application model.
     */
    private CandidateResponseValidationException endingIdsViolation(int stagePosition, int stageIndex,
            String rejectedValue, JsonNode rejectedCandidate, String message) {
        return new CandidateResponseValidationException(List.of(new ProjectionViolation(
                "ENDING_IDS_MISSING", stagePosition, "stages[" + stageIndex + "].endingIds",
                rejectedValue, "", ProjectionViolation.Repairability.REPAIRABLE, message)), null, rejectedCandidate);
    }

    private JsonNode canonicalizeProjection(JsonNode input, List<SourceCitation> authoritativeCitations, Configuration configuration) {
        if (input == null || !input.isObject()) return input;
        ObjectNode rejectedCandidate = ((ObjectNode) input).deepCopy();
        ObjectNode root = ((ObjectNode) input).deepCopy();
        JsonNode stages = root.get("stages");
        // endingIds are canonical plan data, not a projection decision. Preserve
        // the values already present in the candidate and copy the authoritative
        // set to any stage where the provider emitted an empty array.
        copyCanonicalEndingIds(root, configuration);
        Map<String, SourceCitation> registry = new java.util.LinkedHashMap<>();
        for (SourceCitation citation : authoritativeCitations == null ? List.<SourceCitation>of() : authoritativeCitations) {
            if (citation != null && citation.citationKey() != null && !citation.citationKey().isBlank()) {
                registry.putIfAbsent(citation.citationKey().trim(), citation);
            }
        }
        if (stages == null || !stages.isArray()) return root;
        List<ProjectionViolation> violations = new ArrayList<>();
        for (int stageIndex = 0; stageIndex < stages.size(); stageIndex++) {
            JsonNode stage = stages.get(stageIndex);
            if (stage.isObject()) canonicalizeStage((ObjectNode) stage, stageIndex, registry, violations);
        }
        enforcePlanCitationCoverage(stages, registry);
        if (!violations.isEmpty()) {
            throw new CandidateResponseValidationException(violations, null, rejectedCandidate);
        }
        return root;
    }

    /** Global coverage is a plan contract, so attach an authoritative fallback without inventing provenance. */
    private void enforcePlanCitationCoverage(JsonNode stages, Map<String, SourceCitation> registry) {
        if (stages == null || !stages.isArray() || stages.isEmpty()) return;
        Set<String> presentTypes = new java.util.LinkedHashSet<>();
        for (JsonNode stage : stages) {
            stage.path("evidence").forEach(item -> {
                SourceCitation citation = registry.get(item.path("citationKey").asText(""));
                if (citation != null && citation.documentType() != null) {
                    presentTypes.add(citation.documentType().toUpperCase(java.util.Locale.ROOT));
                }
            });
        }
        Set<String> requiredTypes = registry.values().stream()
                .map(SourceCitation::documentType)
                .filter(java.util.Objects::nonNull)
                .map(value -> value.toUpperCase(java.util.Locale.ROOT))
                .filter(type -> type.equals("STORYBOOK") || type.equals("RULEBOOK"))
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        ObjectNode firstStage = (ObjectNode) stages.get(0);
        ArrayNode evidence = firstStage.withArray("evidence");
        for (String type : requiredTypes) {
            if (presentTypes.contains(type)) continue;
            registry.entrySet().stream()
                    .filter(entry -> type.equalsIgnoreCase(entry.getValue().documentType()))
                    .findFirst().ifPresent(entry -> {
                        evidence.addObject().put("citationKey", entry.getKey());
                        LOGGER.info("story_plan_plan_level_citation_coverage_attached type={} citationKey={} stage={}",
                                type, entry.getKey(), stagePosition(firstStage, 0));
                    });
        }
    }

    private void copyCanonicalEndingIds(ObjectNode root, Configuration configuration) {
        JsonNode stages = root.get("stages");
        if (stages == null || !stages.isArray()) return;
        Set<String> canonical = new LinkedHashSet<>();
        stages.forEach(stage -> {
            JsonNode endings = stage.isObject() ? stage.get("endingIds") : null;
            if (endings != null && endings.isArray()) {
                endings.forEach(value -> {
                    if (value.isTextual() && !value.asText().isBlank()) canonical.add(value.asText());
                });
            }
        });
        if (canonical.isEmpty() && configuration != null) {
            for (int i = 1; i <= configuration.endingCount(); i++) canonical.add("ending-" + i);
        }
        if (canonical.isEmpty()) return;
        stages.forEach(stage -> {
            if (!stage.isObject()) return;
            JsonNode endings = stage.get("endingIds");
            if (endings == null || !endings.isArray() || endings.isEmpty()) {
                ArrayNode copied = mapper.createArrayNode();
                canonical.forEach(copied::add);
                ((ObjectNode) stage).set("endingIds", copied);
            }
        });
    }

    private void canonicalizeStage(ObjectNode stage, int stageIndex, Map<String, SourceCitation> registry,
            List<ProjectionViolation> violations) {
        // schemaVersion is a server-owned contract marker. Older providers may
        // omit it even when they emit the current projection fields; do not let
        // that omission downgrade an otherwise canonical v2 response.
        stage.put("schemaVersion", STORY_PLAN_SCHEMA_VERSION);
        Set<String> evidenceKeys = new java.util.LinkedHashSet<>();
        ArrayNode evidence = mapper.createArrayNode();
        JsonNode rawEvidence = stage.get("evidence");
        if (rawEvidence != null && rawEvidence.isArray()) {
            for (JsonNode item : rawEvidence) {
                String key = item.path("citationKey").asText("").trim();
                if (key.isBlank() || !registry.containsKey(key)) {
                    violations.add(new ProjectionViolation(
                            "UNKNOWN_CITATION", stagePosition(stage, stageIndex),
                            "stages[" + stageIndex + "].evidence[*].citationKey", key,
                            "authoritative citation registry", ProjectionViolation.Repairability.REPAIRABLE,
                            "stage " + stagePosition(stage, stageIndex) + " uses an unknown citation key"));
                } else if (evidenceKeys.add(key)) {
                    evidence.addObject().put("citationKey", key);
                }
            }
        }
        stage.set("evidence", evidence);

        ObjectNode skeleton = stage.get("combatSkeleton") != null && stage.get("combatSkeleton").isObject()
                ? (ObjectNode) stage.get("combatSkeleton") : mapper.createObjectNode();
        ArrayNode participants = mapper.createArrayNode();
        JsonNode rawParticipants = skeleton.get("participants");
        if (rawParticipants != null && rawParticipants.isArray()) {
            for (int participantIndex = 0; participantIndex < rawParticipants.size(); participantIndex++) {
                JsonNode item = rawParticipants.get(participantIndex);
                if (!item.isObject()) continue;
                String name = text(item, "name", "");
                String participantId = text(item, "participantId", "");
                String role = text(item, "role", "").toUpperCase(java.util.Locale.ROOT);
                if (participantId.isBlank() || (!role.equals("ENEMY") && !role.equals("BOSS"))) {
                    violations.add(new ProjectionViolation(
                            "INVALID_COMBAT_PARTICIPANT", stagePosition(stage, stageIndex),
                            "stages[" + stageIndex + "].combatSkeleton.participants[" + participantIndex + "]",
                            name, "participantId and role", ProjectionViolation.Repairability.REPAIRABLE,
                            "stage " + stagePosition(stage, stageIndex)
                                    + " combat participant requires participantId and role ENEMY or BOSS"));
                    continue;
                }
                List<String> keys = supportedKeys(item.get("citationKeys"), registry, evidenceKeys, name);
                if (keys.isEmpty() && stringsOrEmpty(item.get("citationKeys")).isEmpty()) {
                    keys = supportedKeys(evidenceKeys, registry, name);
                }
                if (name.isBlank()) continue;
                if (keys.isEmpty()) {
                    List<String> rawKeys = stringsOrEmpty(item.get("citationKeys"));
                    LOGGER.warn("story_plan_combat_participant_grounding_failed stage={} participantIndex={} participantId={} role={} name={} citationKeys={} evidenceKeys={} registeredKeys={} supportedKeys={}",
                            stagePosition(stage, stageIndex), participantIndex,
                            text(item, "participantId", ""), text(item, "role", ""), name,
                            rawKeys, evidenceKeys, registry.keySet(), keys);
                    violations.add(new ProjectionViolation(
                            "COMBAT_PARTICIPANT_SOURCE_UNSUPPORTED", stagePosition(stage, stageIndex),
                            "stages[" + stageIndex + "].combatSkeleton.participants[" + participantIndex + "].name",
                            name, item.path("citationKeys").toString(),
                            ProjectionViolation.Repairability.REPAIRABLE,
                            "stage " + stagePosition(stage, stageIndex)
                                    + " combat participant is not supported by its field-specific source"));
                    continue;
                }
                ObjectNode participant = ((ObjectNode) item).deepCopy();
                participant.put("name", name);
                participant.set("citationKeys", textArray(keys));
                int minimum = positiveInt(item, "minimumCount", 1);
                int maximum = positiveInt(item, "maximumCount", minimum);
                if (!supportsCount(keys, registry, minimum, maximum)) {
                    minimum = 1;
                    maximum = 1;
                }
                participant.put("minimumCount", minimum);
                participant.put("maximumCount", Math.max(minimum, maximum));
                participants.add(participant);
            }
        }
        skeleton.set("participants", participants);
        if (participants.isEmpty()) {
            skeleton.put("objective", "");
            skeleton.put("startTrigger", "");
            skeleton.put("successOutcome", "");
            skeleton.put("failureOutcome", "");
            skeleton.set("rewards", mapper.createArrayNode());
        } else {
            canonicalizeRewards(skeleton, registry, evidenceKeys);
        }
        stage.set("combatSkeleton", skeleton);

        String tacticalRequirement = "REQUIRED".equalsIgnoreCase(text(stage, "combatRequirement", "NONE"))
                && !text(stage, "mapDefinitionId", "").isBlank() ? "REQUIRED" : "NOT_REQUIRED";
        String suppliedTacticalRequirement = text(stage, "tacticalPreparationRequirement", "NOT_REQUIRED");
        if (!suppliedTacticalRequirement.equalsIgnoreCase(tacticalRequirement)) {
            LOGGER.info("story_plan_tactical_requirement_normalized stage={} supplied={} normalized={}",
                    stagePosition(stage, stageIndex), suppliedTacticalRequirement, tacticalRequirement);
        }
        stage.put("tacticalPreparationRequirement", tacticalRequirement);

        filterSupportedCombatHints(stage, registry);
        canonicalizeClaims(stage, registry, evidenceKeys, participants.size(), skeleton.path("rewards").size());

        String requirement = text(stage, "combatRequirement", "NONE").toUpperCase(java.util.Locale.ROOT);
        if (participants.isEmpty()) {
            // Narrative combat hints are a supported possibility even when the
            // provider omitted a concrete sourced participant. Keeping NONE in
            // that case violates the downstream combat validator contract.
            stage.put("combatRequirement", requirement.equals("NONE") ? "POSSIBLE"
                    : (requirement.equals("POSSIBLE") ? "POSSIBLE" : "NONE"));
            stage.put("tacticalPreparationRequirement", "NOT_REQUIRED");
        } else if (!requirement.equals("REQUIRED")) {
            stage.put("combatRequirement", "POSSIBLE");
            stage.put("tacticalPreparationRequirement", "NOT_REQUIRED");
        }
    }

    private static int stagePosition(ObjectNode stage, int stageIndex) {
        return stage.path("position").isIntegralNumber() ? stage.path("position").asInt() : stageIndex + 1;
    }

    private static boolean hasCombatHint(ObjectNode stage) {
        if (!optionalStrings(stage.get("enemies")).isEmpty() || !text(stage, "boss", "").isBlank()) return true;
        String stageType = text(stage, "stageType", "").toUpperCase(java.util.Locale.ROOT);
        if (stageType.equals("ENCOUNTER") || stageType.equals("FINALE")) return true;
        String text = String.join(" ", text(stage, "title", ""), text(stage, "goal", ""),
                text(stage, "conflict", ""), text(stage, "transitionCondition", ""),
                text(stage, "clearCondition", ""), text(stage, "failureCondition", ""),
                String.join(" ", optionalStrings(stage.get("npcOrClues")))).toLowerCase(java.util.Locale.ROOT);
        return text.matches(".*(combat|battle|fight|encounter|ambush|enemy|boss|monster|attack|전투|적|보스|괴물|습격|싸움|거미|쥐).*");
    }

    private void canonicalizeRewards(ObjectNode skeleton, Map<String, SourceCitation> registry, Set<String> evidenceKeys) {
        ArrayNode rewards = mapper.createArrayNode();
        JsonNode raw = skeleton.get("rewards");
        if (raw != null && raw.isArray()) {
            raw.forEach(item -> {
                if (!item.isObject()) return;
                String path = text(item, "fieldPath", "");
                String claim = text(item, "normalizedClaim", "");
                if (!path.matches("combatSkeleton\\.rewards\\[[0-9]+\\]") || claim.isBlank()) return;
                List<String> keys = supportedKeys(item.get("citationKeys"), registry, evidenceKeys, claim);
                if (!keys.isEmpty()) {
                    ObjectNode reward = ((ObjectNode) item).deepCopy();
                    reward.put("fieldPath", path);
                    reward.put("normalizedClaim", claim);
                    reward.set("citationKeys", textArray(keys));
                    rewards.add(reward);
                }
            });
        }
        skeleton.set("rewards", rewards);
    }

    private void canonicalizeClaims(ObjectNode stage, Map<String, SourceCitation> registry, Set<String> evidenceKeys,
            int participantCount, int rewardCount) {
        ArrayNode claims = mapper.createArrayNode();
        JsonNode raw = stage.get("sourceFactClaims");
        if (raw != null && raw.isArray()) {
            raw.forEach(item -> {
                if (!item.isObject()) return;
                String path = text(item, "fieldPath", "");
                if (!allowedCombatClaimPath(path, participantCount, rewardCount)) return;
                String claim = text(item, "normalizedClaim", "");
                if (claim.isBlank()) return;
                List<String> keys = supportedKeys(item.get("citationKeys"), registry, evidenceKeys, claim);
                String origin = text(item, "origin", "SOURCE").toUpperCase(java.util.Locale.ROOT);
                if (origin.equals("SOURCE") && keys.isEmpty()) return;
                ObjectNode normalized = ((ObjectNode) item).deepCopy();
                normalized.put("fieldPath", path);
                normalized.put("normalizedClaim", claim);
                normalized.set("citationKeys", textArray(keys));
                normalized.put("origin", origin);
                claims.add(normalized);
            });
        }
        stage.set("sourceFactClaims", claims);
    }

    private static boolean allowedCombatClaimPath(String path, int participantCount, int rewardCount) {
        if (path.matches("combatSkeleton\\.(objective|startTrigger|successOutcome|failureOutcome)")) return true;
        Matcher participant = Pattern.compile("combatSkeleton\\.participants\\[(\\d+)\\]\\.(participantId|role|name|minimumCount|maximumCount)").matcher(path);
        if (participant.matches()) return Integer.parseInt(participant.group(1)) < participantCount;
        Matcher reward = Pattern.compile("combatSkeleton\\.rewards\\[(\\d+)\\]").matcher(path);
        return reward.matches() && Integer.parseInt(reward.group(1)) < rewardCount;
    }

    private static List<String> supportedKeys(JsonNode raw, Map<String, SourceCitation> registry,
            Set<String> evidenceKeys, String claim) {
        Set<String> result = new java.util.LinkedHashSet<>();
        if (raw != null && raw.isArray()) raw.forEach(item -> {
            String key = item.asText("").trim();
            SourceCitation citation = registry.get(key);
            if (!key.isBlank() && evidenceKeys.contains(key) && citation != null && supports(citation.quote(), claim)) result.add(key);
        });
        return List.copyOf(result);
    }

    private static List<String> supportedKeys(Iterable<String> raw, Map<String, SourceCitation> registry, String claim) {
        Set<String> result = new java.util.LinkedHashSet<>();
        for (String key : raw) {
            SourceCitation citation = registry.get(key);
            if (citation != null && supports(citation.quote(), claim)) result.add(key);
        }
        return List.copyOf(result);
    }

    private static boolean supports(String quote, String claim) {
        String source = normalize(quote);
        String value = normalize(claim);
        return !value.isBlank() && source.contains(value);
    }

    private static int positiveInt(JsonNode source, String field, int fallback) {
        int value = source == null ? 0 : source.path(field).asInt(0);
        return value > 0 ? value : fallback;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private static boolean supportsCount(List<String> keys, Map<String, SourceCitation> registry, int minimum, int maximum) {
        if (minimum == 1 && maximum == 1) return true;
        return keys.stream().map(registry::get).anyMatch(citation -> citation != null
                && normalize(citation.quote()).matches(".*(^| )" + minimum + "( |$).*"));
    }

    private static ArrayNode textArray(List<String> values) {
        ArrayNode result = new ObjectMapper().createArrayNode();
        values.forEach(result::add);
        return result;
    }

    private static void filterSupportedCombatHints(ObjectNode stage, Map<String, SourceCitation> registry) {
        if (stage.get("enemies") != null && stage.get("enemies").isArray()) {
            ArrayNode enemies = new ObjectMapper().createArrayNode();
            stage.get("enemies").forEach(item -> { String value = item.asText("").trim(); if (registry.values().stream().anyMatch(c -> supports(c.quote(), value))) enemies.add(value); });
            stage.set("enemies", enemies);
        }
        String boss = text(stage, "boss", "");
        if (!boss.isBlank() && registry.values().stream().noneMatch(c -> supports(c.quote(), boss))) stage.put("boss", "");
    }
    private static String extractObject(String text) { int a = text.indexOf('{'), b = text.lastIndexOf('}'); if (a < 0 || b < a) throw new IllegalArgumentException("JSON object missing"); return text.substring(a,b+1); }
    private static String required(JsonNode n, String key) { String v = n.path(key).asText("").trim(); if (v.isBlank()) throw new IllegalArgumentException(key + " missing"); return v; }
    private static List<String> strings(JsonNode n, String field) {
        if (n == null || !n.isArray()) throw new IllegalArgumentException(field + " must be explicit");
        List<String> r = new ArrayList<>();
        n.forEach(v -> { if (v.isTextual() && !v.asText().isBlank()) r.add(v.asText()); });
        return List.copyOf(r);
    }
    private static List<String> optionalStrings(JsonNode n) {
        if (n == null || n.isNull()) return List.of();
        if (!n.isArray()) throw new IllegalArgumentException("optional collection must be an array");
        List<String> result = new ArrayList<>();
        n.forEach(item -> { if (item.isTextual() && !item.asText().isBlank()) result.add(item.asText().trim()); });
        return List.copyOf(result);
    }
    private static Map<String, String> optionalMaps(JsonNode n) {
        return n == null || n.isNull() ? Map.of() : maps(n, "branchTargets");
    }
    private static List<CitationProjection> optionalCitations(JsonNode n) {
        return n == null || n.isNull() ? List.of() : citations(n, "evidence");
    }
    private static CombatSkeletonProjection parseCombatSkeleton(JsonNode node) {
        if (node == null || node.isNull()) return new CombatSkeletonProjection("", "", List.of(), "", "", List.of());
        List<CombatParticipantProjection> participants = optionalObjects(node.get("participants"), item ->
                new CombatParticipantProjection(text(item, "participantId", "participant-" + item.path("name").asText("unknown")),
                        text(item, "role", "ENEMY"), required(item, "name"), item.path("minimumCount").asInt(1),
                        item.path("maximumCount").asInt(item.path("minimumCount").asInt(1)), stringsOrEmpty(item.get("citationKeys"))));
        List<SourceFactClaimProjection> rewards = optionalObjects(node.get("rewards"), AdventureStoryPlanController::sourceFactClaim);
        return new CombatSkeletonProjection(text(node, "objective", ""), text(node, "startTrigger", ""), participants,
                text(node, "successOutcome", ""), text(node, "failureOutcome", ""), rewards);
    }
    private static SourceFactClaimProjection sourceFactClaim(JsonNode node) {
        return new SourceFactClaimProjection(required(node, "fieldPath"), required(node, "normalizedClaim"), stringsOrEmpty(node.get("citationKeys")), text(node, "origin", "SOURCE"));
    }
    private static List<SourceFactClaimProjection> parseSourceFactClaims(JsonNode node) {
        return optionalObjects(node, AdventureStoryPlanController::sourceFactClaim);
    }
    private static List<String> stringsOrEmpty(JsonNode node) {
        return node == null || node.isNull() ? List.of() : strings(node, "citationKeys");
    }
    private static <T> List<T> optionalObjects(JsonNode node, java.util.function.Function<JsonNode, T> mapper) {
        if (node == null || node.isNull()) return List.of();
        if (!node.isArray()) throw new IllegalArgumentException("optional collection must be an array");
        List<T> result = new ArrayList<>();
        node.forEach(item -> { if (!item.isObject()) throw new IllegalArgumentException("collection item must be an object"); result.add(mapper.apply(item)); });
        return List.copyOf(result);
    }
    private static String text(JsonNode node, String key, String fallback) { String value = node.path(key).asText("").trim(); return value.isBlank() ? fallback : value; }
    private static Map<String, String> maps(JsonNode node, String field) {
        if (node == null || !node.isObject()) throw new IllegalArgumentException(field + " must be explicit");
        Map<String, String> result = new HashMap<>();
        node.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().asText()));
        return Map.copyOf(result);
    }
    private static List<CitationProjection> citations(JsonNode node, String field) {
        if (node == null || !node.isArray()) throw new IllegalArgumentException(field + " must be explicit");
        List<CitationProjection> result = new ArrayList<>();
        node.forEach(item -> {
            result.add(new CitationProjection(required(item, "citationKey")));
        });
        return List.copyOf(result);
    }
    static final class CandidateResponseValidationException extends RuntimeException {
        private final List<String> violations;
        private final List<ProjectionViolation> structuredViolations;
        private final JsonNode rejectedCandidate;
        CandidateResponseValidationException(String message, Throwable cause) {
            this(List.of(message), cause);
        }
        CandidateResponseValidationException(List<?> violations, Throwable cause) {
            this(violations, cause, null);
        }
        CandidateResponseValidationException(List<?> violations, Throwable cause, JsonNode rejectedCandidate) {
            super(violations == null ? "candidate validation failed" : violations.stream()
                    .map(item -> item instanceof ProjectionViolation violation ? violation.sanitizedMessage()
                            : sanitizeDiagnostic(String.valueOf(item)))
                    .reduce((left, right) -> sanitizeDiagnostic(left + "; " + right))
                    .orElse("candidate validation failed"), cause);
            if (violations == null || violations.isEmpty()) throw new IllegalArgumentException("candidate violations must not be empty");
            this.structuredViolations = violations.stream().map(CandidateResponseValidationException::toProjectionViolation).toList();
            this.violations = this.structuredViolations.stream().map(ProjectionViolation::sanitizedMessage).toList();
            this.rejectedCandidate = rejectedCandidate;
        }
        List<String> violations() { return violations; }
        List<ProjectionViolation> structuredViolations() { return structuredViolations; }
        JsonNode rejectedCandidate() { return rejectedCandidate; }

        private static ProjectionViolation toProjectionViolation(Object value) {
            if (value instanceof ProjectionViolation violation) return violation;
            String message = String.valueOf(value).trim();
            String normalized = message.toLowerCase(java.util.Locale.ROOT);
            java.util.regex.Matcher stageMatcher = java.util.regex.Pattern.compile("(?i)stage\\s+(\\d+)").matcher(message);
            Integer stagePosition = stageMatcher.find() ? Integer.valueOf(stageMatcher.group(1)) : null;
            boolean missingEndingIds = normalized.contains("endingids")
                    && (normalized.contains("missing") || normalized.contains("empty")
                    || normalized.contains("explicit") || normalized.contains("required"));
            String field = missingEndingIds ? "stages[*].endingIds"
                    : normalized.contains("transitioncondition") ? "stages[*].transitionCondition"
                    : normalized.contains("clearcondition") ? "stages[*].clearCondition"
                    : normalized.contains("failurecondition") ? "stages[*].failureCondition"
                    : normalized.contains("citation") ? "stages[*].evidence[*].citationKey" : "stages";
            if (stagePosition != null && field.startsWith("stages[*].")) {
                field = field.replace("stages[*]", "stages[" + (stagePosition - 1) + "]");
            }
            String code = missingEndingIds ? "ENDING_IDS_MISSING"
                    : normalized.contains("citation") ? "CITATION_CONTRACT_VIOLATION" : "PROJECTION_FIELD_INVALID";
            ProjectionViolation.Repairability repairability = missingEndingIds
                    ? ProjectionViolation.Repairability.REPAIRABLE
                    : normalized.contains("citation")
                    ? ProjectionViolation.Repairability.SOURCE_EVIDENCE_INSUFFICIENT
                    : field.equals("stages") ? ProjectionViolation.Repairability.REGENERATE_REQUIRED
                    : ProjectionViolation.Repairability.REPAIRABLE;
            String detail = message.contains(":") ? message.substring(message.lastIndexOf(':') + 1).trim() : "";
            String safeMessage = message.contains(":") ? message.substring(0, message.indexOf(':')).trim() : message;
            String citationContext = normalized.contains("citation") ? detail : "";
            return new ProjectionViolation(code, stagePosition, field, detail, citationContext, repairability, safeMessage);
        }
    }
    public record Request(String operationId, long packageRevision, int partySize, Configuration configuration, List<String> sourceDocuments,
            List<String> resolutionEvidence, List<MapContext> maps, List<SourceCitation> citations, List<String> violations,
            String previousCandidate, String generationMode, JsonNode sourceConstraintPack,
            RetrievalContext retrievalContext) {
        public Request(String operationId, long packageRevision, int partySize, Configuration configuration, List<String> sourceDocuments,
                List<String> resolutionEvidence, List<MapContext> maps, List<SourceCitation> citations, List<String> violations,
                String previousCandidate) {
            this(operationId, packageRevision, partySize, configuration, sourceDocuments, resolutionEvidence, maps, citations,
                    violations, previousCandidate, "GENERATIVE", null, RetrievalContext.empty());
        }
        public Request(String operationId, long packageRevision, int partySize, Configuration configuration,
                List<String> sourceDocuments, List<String> resolutionEvidence,
                List<MapContext> maps, List<SourceCitation> citations) {
            this(operationId, packageRevision, partySize, configuration, sourceDocuments,
                    resolutionEvidence, maps, citations, List.of(), "", "GENERATIVE", null, RetrievalContext.empty());
        }
        public Request(String operationId, long packageRevision, int partySize, Configuration configuration, List<String> sourceDocuments, List<String> resolutionEvidence) {
            this(operationId, packageRevision, partySize, configuration, sourceDocuments,
                    resolutionEvidence, List.of(), List.of(), List.of(), "", "GENERATIVE", null, RetrievalContext.empty());
        }
        public Request {
            violations = violations == null ? List.of() : List.copyOf(violations);
            previousCandidate = previousCandidate == null ? "" : previousCandidate;
            generationMode = generationMode == null || generationMode.isBlank() ? "GENERATIVE" : generationMode;
            retrievalContext = retrievalContext == null ? RetrievalContext.empty() : retrievalContext;
        }
    }
    public record VerificationRequest(String operationId, Configuration configuration, List<String> sourceDocuments,
            List<String> resolutionEvidence, List<MapContext> maps, List<SourceCitation> citations, String generatedMarkdown,
            RetrievalContext retrievalContext) {
        public VerificationRequest {
            sourceDocuments = sourceDocuments == null ? List.of() : List.copyOf(sourceDocuments);
            resolutionEvidence = resolutionEvidence == null ? List.of() : List.copyOf(resolutionEvidence);
            maps = maps == null ? List.of() : List.copyOf(maps);
            citations = citations == null ? List.of() : List.copyOf(citations);
            retrievalContext = retrievalContext == null ? RetrievalContext.empty() : retrievalContext;
        }
    }
    public record VerificationResponse(String status, List<String> violations) {
        public VerificationResponse { violations = List.copyOf(violations); }
    }
    public record CandidateValidationError(List<String> violations, List<ProjectionViolation> structuredViolations,
            JsonNode rejectedCandidate) {
        public CandidateValidationError(List<String> violations) {
            this(violations, violations.stream().map(CandidateResponseValidationException::toProjectionViolation).toList(), null);
        }
        public CandidateValidationError {
            violations = List.copyOf(violations);
            structuredViolations = List.copyOf(structuredViolations);
        }
    }
    public record MapContext(String mapDefinitionId, String assetId, String assetLocator, String sourceLocator,
            double confidence, String safetyStatus, List<SourceCitation> relatedEvidence, String context) {
        public MapContext(String mapDefinitionId, String assetId, String assetLocator, String sourceLocator,
                double confidence, String safetyStatus, List<SourceCitation> relatedEvidence) {
            this(mapDefinitionId, assetId, assetLocator, sourceLocator, confidence, safetyStatus, relatedEvidence, "");
        }
        public MapContext {
            relatedEvidence = relatedEvidence == null ? List.of() : List.copyOf(relatedEvidence);
            context = context == null ? "" : context;
        }
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SourceCitation(String documentType, String documentId, long extractionVersion, String locator, String quote,
            double confidence, @JsonInclude(JsonInclude.Include.NON_EMPTY) String citationKey) {
        public SourceCitation(String documentType, String documentId, long extractionVersion, String locator,
                String quote, double confidence) {
            this(documentType, documentId, extractionVersion, locator, quote, confidence, "");
        }
    }
    public record RetrievalContext(UUID ownerId, List<RetrievalDocument> documents) {
        public RetrievalContext {
            documents = documents == null ? List.of() : List.copyOf(documents);
        }

        public static RetrievalContext empty() {
            return new RetrievalContext(null, List.of());
        }
    }
    public record RetrievalDocument(String documentType, UUID documentId, long extractionVersion) {}
    public record CitationProjection(String citationKey) {}
    public record ProjectionViolation(String code, Integer stagePosition, String fieldPath, String rejectedValue,
            String citationContext, Repairability repairability, String sanitizedMessage) {
        public enum Repairability { REPAIRABLE, REGENERATE_REQUIRED, SOURCE_EVIDENCE_INSUFFICIENT, SYSTEM_CONTRACT_ERROR }
        public ProjectionViolation {
            rejectedValue = sanitizeDiagnostic(rejectedValue == null ? "" : rejectedValue);
            citationContext = sanitizeDiagnostic(citationContext == null ? "" : citationContext);
            sanitizedMessage = sanitizeDiagnostic(sanitizedMessage == null ? "projection validation failed" : sanitizedMessage);
        }
    }
    public record RepairRequest(String operationId, long packageRevision, int partySize, Configuration configuration,
            JsonNode previousCandidate, List<ProjectionViolation> violations, List<String> sourceDocuments,
            List<String> resolutionEvidence, List<MapContext> maps, List<SourceCitation> citations,
            RepairScope repairScope, RetrievalContext retrievalContext) {
        public RepairRequest(String operationId, long packageRevision, int partySize, Configuration configuration,
                JsonNode previousCandidate, List<ProjectionViolation> violations, List<String> sourceDocuments,
                List<String> resolutionEvidence, List<MapContext> maps, List<SourceCitation> citations) {
            this(operationId, packageRevision, partySize, configuration, previousCandidate, violations, sourceDocuments,
                    resolutionEvidence, maps, citations, RepairScope.from(violations), RetrievalContext.empty());
        }
        public RepairRequest {
            violations = violations == null ? List.of() : List.copyOf(violations);
            sourceDocuments = sourceDocuments == null ? List.of() : List.copyOf(sourceDocuments);
            resolutionEvidence = resolutionEvidence == null ? List.of() : List.copyOf(resolutionEvidence);
            maps = maps == null ? List.of() : List.copyOf(maps);
            citations = citations == null ? List.of() : List.copyOf(citations);
            retrievalContext = retrievalContext == null ? RetrievalContext.empty() : retrievalContext;
            if (repairScope == null) {
                throw new IllegalArgumentException("deterministic repair scope must be explicit");
            }
        }
    }
    public record RepairScope(List<String> blockerPaths, List<String> dependentPaths, List<String> allowedPaths,
            boolean regenerationRequired) {
        public RepairScope {
            blockerPaths = blockerPaths == null ? List.of() : List.copyOf(blockerPaths);
            dependentPaths = dependentPaths == null ? List.of() : List.copyOf(dependentPaths);
            allowedPaths = allowedPaths == null ? List.of() : List.copyOf(allowedPaths);
        }

        static RepairScope from(List<ProjectionViolation> violations) {
            List<String> blockers = violations == null ? List.of() : violations.stream().map(ProjectionViolation::fieldPath).toList();
            return new RepairScope(blockers, List.of(), blockers, false);
        }
    }
    public record Configuration(int endingCount, String adventureLength) {
        public Configuration {
            if (endingCount < 1 || endingCount > 4) throw new IllegalArgumentException("ending count must be between 1 and 4");
            if (!List.of("SHORT", "STANDARD", "LONG").contains(adventureLength)) throw new IllegalArgumentException("unknown adventure length");
        }
        static Configuration defaults() { return new Configuration(2, "STANDARD"); }
        int minimumStages() { return switch (adventureLength) { case "SHORT" -> 3; case "STANDARD" -> 4; default -> 7; }; }
        int maximumStages() { return switch (adventureLength) { case "SHORT" -> 4; case "STANDARD" -> 6; default -> 8; }; }
    }
    public record Response(List<Stage> stages, String markdown) {
        public Response(List<Stage> stages) { this(stages, ""); }
        public Response { stages = stages == null ? List.of() : List.copyOf(stages); markdown = markdown == null ? "" : markdown; }
    }
    public record Stage(int position, String title, String stageType, String location, String goal, String conflict, String transitionCondition,
            List<String> npcOrClues, List<String> endingIds, String mapDefinitionId, String mapAssetId, String mapAssetLocator, List<String> enemies, String boss,
            String clearCondition, String failureCondition, List<String> rewards, List<String> branchIds, Map<String, String> branchTargets, List<CitationProjection> evidence,
            String combatRequirement, CombatSkeletonProjection combatSkeleton, List<SourceFactClaimProjection> sourceFactClaims,
            String tacticalPreparationRequirement, int schemaVersion) {}
    public record CombatSkeletonProjection(String objective, String startTrigger, List<CombatParticipantProjection> participants,
            String successOutcome, String failureOutcome, List<SourceFactClaimProjection> rewards) {}
    public record CombatParticipantProjection(String participantId, String role, String name, int minimumCount, int maximumCount,
            List<String> citationKeys) {}
    public record SourceFactClaimProjection(String fieldPath, String normalizedClaim, List<String> citationKeys, String origin) {
        public SourceFactClaimProjection(String fieldPath, String normalizedClaim, List<String> citationKeys) {
            this(fieldPath, normalizedClaim, citationKeys, "SOURCE");
        }
    }
}
