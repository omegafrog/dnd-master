package com.dndmaster.aigamemaster.api;

import com.dndmaster.aigamemaster.infrastructure.ai.SpringAiChatAdapter;
import com.dndmaster.aigamemaster.infrastructure.ai.CodexCliStoryPlanAdapter;
import com.dndmaster.aigamemaster.infrastructure.ai.AiCallObservability;
import com.dndmaster.aigamemaster.application.endpoint.AgentEndpoint;
import com.dndmaster.aigamemaster.application.endpoint.AgentEndpointRegistry;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Pattern RESOLUTION_FIELD = Pattern.compile("(?im)^(\\s*-\\s*DC\\s+or\\s+dice:\\s*)(.*)$");
    private static final Pattern DC_VALUE = Pattern.compile("(?i)\\bDC\\s*(\\d+)\\b");
    private final SpringAiChatAdapter adapter; private final ObjectMapper mapper; private final AgentEndpointRegistry endpointRegistry;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final URI ollamaBaseUrl; private final String ollamaModel;
    private final String codexExecutable; private final java.nio.file.Path codexWorkDirectory; private final Duration codexTimeout;
    private final String codexReasoning;
    private final ApiRequestGuard requestGuard;
    public AdventureStoryPlanController(SpringAiChatAdapter adapter, ObjectMapper mapper, AgentEndpointRegistry endpointRegistry,
            @Value("${local-ai.ollama.base-url:http://127.0.0.1:11434}") String ollamaBaseUrl,
            @Value("${local-ai.ollama.chat-model:qwen3:8b}") String ollamaModel,
            @Value("${ai.codex.executable:codex}") String codexExecutable,
            @Value("${ai.codex.work-directory:.}") String codexWorkDirectory,
            @Value("${ai.codex.timeout:PT5M}") Duration codexTimeout,
            @Value("${ai.codex.reasoning:${GM_REASONING:medium}}") String codexReasoning,
            @Value("${ai-game-master.integration.internal-token:${INTERNAL_SERVICE_TOKEN:}}") String internalToken) {
        this(adapter, mapper, endpointRegistry, ollamaBaseUrl, ollamaModel, codexExecutable, codexWorkDirectory, codexTimeout,
                codexReasoning, new ApiRequestGuard(internalToken));
    }
    public AdventureStoryPlanController(SpringAiChatAdapter adapter, ObjectMapper mapper, AgentEndpointRegistry endpointRegistry,
            String ollamaBaseUrl, String ollamaModel, String codexExecutable, String codexWorkDirectory, Duration codexTimeout,
            ApiRequestGuard requestGuard) {
        this(adapter, mapper, endpointRegistry, ollamaBaseUrl, ollamaModel, codexExecutable, codexWorkDirectory, codexTimeout, "medium", requestGuard);
    }
    public AdventureStoryPlanController(SpringAiChatAdapter adapter, ObjectMapper mapper, AgentEndpointRegistry endpointRegistry,
            String ollamaBaseUrl, String ollamaModel, String codexExecutable, String codexWorkDirectory, Duration codexTimeout,
            String codexReasoning, ApiRequestGuard requestGuard) {
        this.adapter = adapter; this.mapper = mapper; this.endpointRegistry = endpointRegistry; this.ollamaBaseUrl = URI.create(ollamaBaseUrl); this.ollamaModel = ollamaModel;
        this.codexExecutable = codexExecutable; this.codexWorkDirectory = java.nio.file.Path.of(codexWorkDirectory); this.codexTimeout = codexTimeout; this.codexReasoning = codexReasoning;
        this.requestGuard = requestGuard;
    }
    @PostMapping("/internal/v1/gm/adventure-story-plan")
    Response generate(@RequestHeader(value = "X-Internal-Token", required = false) String internalToken, @RequestBody Request request) {
        requestGuard.internal(internalToken);
        AgentEndpoint endpoint = endpointRegistry.active();
        LOGGER.info("story_plan_generation_started operationId={} provider={} model={} reasoning={}", AiCallObservability.safe(request.operationId()), endpoint.provider(), AiCallObservability.safe(endpoint.model()), AiCallObservability.safe(codexReasoning));
        Configuration configuration = request.configuration() == null ? Configuration.defaults() : request.configuration();
        String endingTemplate = java.util.stream.IntStream.rangeClosed(1, configuration.endingCount())
                .mapToObj(index -> "## Ending " + index + " (ending-" + index + "): [ending name]\n"
                        + "- Resolution: [what happens]\n- Requirements: [what must be true]\n- Rewards: [final rewards]")
                .collect(java.util.stream.Collectors.joining("\n\n"));
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
                - Map decision: [REQUIRED | OPTIONAL | NONE]
                - Map decision rationale: [why this scene does or does not need a map]
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

                [ENDINGS]
                """.replace("[ENDINGS]", endingTemplate);
        String availableMaps = request.maps().isEmpty() ? "(no maps supplied)" : request.maps().stream()
                .map(map -> "- mapDefinitionId=" + map.mapDefinitionId() + ", assetId=" + map.assetId()
                        + ", assetLocator=" + map.assetLocator() + ", sourceLocator=" + map.sourceLocator()
                        + ", confidence=" + map.confidence() + ", safetyStatus=" + map.safetyStatus()
                        + ", relatedStoryEvidence=" + map.relatedEvidence() + ", mapContext=" + map.context())
                .collect(java.util.stream.Collectors.joining("\n"));
        String prompt = "Create a source-grounded tabletop adventure plan by filling the Markdown template below. "
                + "All player-facing fields (title, location, goal, conflict, clues, enemies, rewards, conditions, and endings) MUST be written in natural Korean. Keep proper nouns in Korean where possible and never mix English prose into player-facing text. "
                + "Return the completed Markdown document only. Replace every bracketed placeholder with concrete content; do not leave placeholders. "
                + "Keep the headings and field labels stable so another agent can read the plan. "
                + "Create " + configuration.minimumStages() + "-" + configuration.maximumStages() + " stages and exactly " + configuration.endingCount() + " endings; duplicate or remove the sample stage/ending sections as needed. "
                + "Use exactly the explicit ending IDs ending-1 through ending-" + configuration.endingCount() + ". Keep these IDs in the ending headings and use only these IDs in every stage's endingIds field. "
                + "HIDDEN-INFORMATION CONTRACT: every hidden-information trigger, secret, clue reveal, conditional event, or rules check MUST state its activation condition and both an explicit success result and an explicit failure or fail-forward consequence. Never leave Success or Failure blank, and do not describe a trigger without its outcomes. "
                + "MAP CONTRACT: decide map usage per stage as REQUIRED, OPTIONAL, or NONE. Use REQUIRED only when tactical positioning, exploration, movement, or spatial interaction materially needs a map; use NONE for scenes that can run without spatial representation. When REQUIRED, copy the exact mapDefinitionId, assetId, and assetLocator from one AVAILABLE MAPS entry. OPTIONAL and NONE stages may leave map fields empty. Never invent a map ID or locator. "
                + "DUNGEON MAP RULE: whenever a stage uses stageType dungeon, it MUST use mapUsage REQUIRED and MUST copy mapDefinitionId, mapAssetId, and mapAssetLocator from the same AVAILABLE MAPS entry. Never emit a dungeon stage with NONE or OPTIONAL when maps are supplied. "
                + "MAP CONTEXT LOOKUP: treat each AVAILABLE MAPS entry as the map-context registry. Match a stage to a map using its location, binding, and source locator; then copy the exact mapDefinitionId, assetId, and assetLocator from that same entry. Use the supplied grid, walls, doors, obstacles, and binding context when describing player spawn or movement. Do not guess a filesystem path and do not create a new map identifier. "
                + "For every mapped dungeon, use the supplied map image/page and the supplied story evidence to infer the party's starting area. In Player spawn, write the semantic entrance and grid coordinates when the map grid makes them identifiable; otherwise state 'GM confirmation required' and explain the evidence. Do not silently default to (0,0). "
                + "TRANSITION CONTRACT: transitionCondition and clearCondition are execution scaffolding, not source facts. Keep them generic and operational (for example, 확보한 단서로 다음 단계로 이동한다) and do not introduce named places, creatures, rewards, DCs, or other claims absent from the citations. "
                + "EVIDENCE COVERAGE CONTRACT: when both STORYBOOK and RULEBOOK citations are supplied, use at least one exact STORYBOOK citation and at least one exact RULEBOOK citation somewhere across the complete plan. Do not satisfy this requirement by repeating only one document type; repair any previous validation violation that names missing coverage. "
                + "Do not invent named rules, DCs, monsters, or facts absent from evidence. For a check without an evidenced DC, write 'GM adjudication' rather than inventing a number. Include checks only when a trigger exists. Documents=" + request.sourceDocuments()
                + " Generation mode=" + request.generationMode() + " Source constraint pack=" + request.sourceConstraintPack()
                + " Evidence=" + request.resolutionEvidence() + " citations=" + request.citations() + " maps=" + request.maps()
                + " Previous validation violations=" + request.violations()
                + " Previous candidate to repair=" + request.previousCandidate()
                + " partySize=" + request.partySize() + " configuration=" + configuration + "\n\nAVAILABLE MAPS (authoritative):\n" + availableMaps
                + "\n\nTEMPLATE:\n" + template;
        try {
            String generatedMarkdown = complete(endpoint, request.operationId(), prompt, configuration);
            if (generatedMarkdown == null || generatedMarkdown.trim().length() < 500) {
                throw new CandidateResponseValidationException(
                        "generated plan is incomplete: return the full adventure plan with goals, stages, map scenes, and endings", null);
            }
            // Providers occasionally copy a plausible DC from their prior context. Repair the
            // narrow resolution field deterministically before the verifier sees it: a numeric
            // value is retained only when that exact value occurs in supplied citations.
            generatedMarkdown = normalizeResolutionValues(generatedMarkdown, request.citations());
            VerificationResponse verification = parseVerificationResponse(complete(endpoint, request.operationId() + "-verification",
                    verificationDecisionPrompt(request, configuration, generatedMarkdown), configuration));
            LOGGER.warn("ai_agent_verification_result operationId={} status={} violationsCount={}", request.operationId(),
                    verification.status(), verification.violations().size());
            if (verification.status().equals("FAIL")) {
                throw new CandidateResponseValidationException(verification.violations(), null);
            }
            String projectedJson = complete(endpoint, request.operationId() + "-execution-projection",
                    projectionPrompt(request, configuration, generatedMarkdown), configuration);
            return new Response(parseJson(projectedJson, configuration));
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
                    repairPrompt(request, configuration), configuration);
            return new Response(parseJson(repaired, configuration));
        } catch (CandidateResponseValidationException invalidCandidate) {
            throw invalidCandidate;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("story plan projection repair interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("story plan projection repair failed: " + rootMessage(e), e);
        }
    }

    private String complete(AgentEndpoint endpoint, String operationId, String prompt, Configuration configuration) throws IOException, InterruptedException {
        long startedAt = System.nanoTime();
        String phase = phase(operationId);
        String model = endpoint.model().isBlank() ? ollamaModel : endpoint.model();
        LOGGER.info("story_plan_stage_started stage={} operationId={} provider={} model={} reasoning={} promptChars={} estimatedPromptTokens={}", phase, AiCallObservability.safe(operationId), endpoint.provider(), AiCallObservability.safe(model), AiCallObservability.safe(codexReasoning), prompt.length(), AiCallObservability.estimatedTokens(prompt.length()));
        String result = null;
        boolean turnCompletedReceived = false;
        boolean timeout = false;
        try {
            if (endpoint.provider() == AgentEndpoint.Provider.CODEX_CLI) {
                result = new CodexCliStoryPlanAdapter(codexExecutable, model, codexWorkDirectory, codexTimeout, codexReasoning).complete(operationId, prompt);
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
                Use only citationKey values copied verbatim from the supplied citation registry; never invent numeric, document-derived, or stage-local citation keys. Do not create sourceFactClaims for goal, conflict, transitionCondition, clearCondition, or any other narrative field: sourceFactClaims are exclusively combat skeleton claims. A combat participant name must match the cited excerpt exactly enough to be supported, and its citationKeys must point to that same registered excerpt. Keep combatRequirement consistent with the stage: NONE has no combat hints, POSSIBLE has no committed participant, and REQUIRED has a complete sourced skeleton. If a combat claim cannot be grounded by an exact supplied citation, omit the combat commitment or regenerate the complete projection rather than guessing.
                Optional fields may be omitted or empty: npcOrClues, enemies, boss, clearCondition, failureCondition, rewards, branchIds, branchTargets, and player spawn fields. When citations are supplied, evidence is REQUIRED for every stage: copy at least one exact citationKey from the supplied citation registry. Do not copy document IDs, extraction versions, locators, quotes, or confidence into evidence. When both STORYBOOK and RULEBOOK citations are supplied, the complete plan MUST include at least one exact citationKey for each type across its stages. A trigger is represented only by a short reference or lookup key; never copy the full trigger or rule text.
                Arrays may be empty arrays and branchTargets may be an empty object. Never invent a map, trigger, citation, enemy, reward, or ending. Use the ending IDs stated in the plan, or a stable structural ending ID when necessary.
                If the plan is invalid, return the best faithful projection so the application can report the violation. Preserve required burning-web or other hazard failure consequences from the verified Markdown as stage failureCondition text; do not turn them into sourceFactClaims or drop them.
                configuration=%s
                sourceDocuments=%s
                resolutionEvidence=%s
                maps=%s
                citations=%s
                previousViolations=%s

                GENERATED MARKDOWN:
                %s
                """.formatted(configuration.endingCount(), configuration.endingCount(), configuration, request.sourceDocuments(), request.resolutionEvidence(), request.maps(), request.citations(), request.violations(), generatedMarkdown);
    }

    private String repairPrompt(RepairRequest request, Configuration configuration) {
        return """
                You are repairing one rejected execution projection. Return the COMPLETE projection JSON object, never a patch.
                Preserve every field exactly unless its JSON path is listed in STRUCTURED VIOLATIONS. Do not add, remove, rename, or
                mutate any unlisted field. Use only the authoritative citation, map, and source registries supplied below; never invent,
                fuzzy-match, or copy a quote, locator, document ID, map ID, or source that is not registered. The server will rerun the
                complete schema, citation/map/source, and business-rule validation chain after this response.
                The response root MUST be an object with a stages array and must contain the full candidate, not a JSON patch. For a failure-consequence violation, repair only the affected stage failureCondition and preserve its concrete fail-forward consequence. For citation or participant violations, use only exact registered keys and keep the claim, participant, and same-stage evidence binding consistent.
                configuration=%s
                structuredViolations=%s
                deterministicRepairScope=%s
                authoritativeSourceDocuments=%s
                authoritativeResolutionEvidence=%s
                authoritativeMaps=%s
                authoritativeCitations=%s
                previousFullCandidate=%s
                """.formatted(configuration, request.violations(), request.repairScope(), request.sourceDocuments(), request.resolutionEvidence(),
                request.maps(), request.citations(), request.previousCandidate());
    }

    private String verificationDecisionPrompt(Request request, Configuration configuration, String generatedMarkdown) {
        return """
                You are an independent verifier for a generated tabletop adventure plan.
                Inspect only whether essential information is present and usable against the supplied source evidence, citations, map contract, and configuration.
                Do not rewrite, summarize, extract, or normalize the plan. Do not return stages or any other plan data.
                Return ONLY one JSON object with exactly these fields: {"status":"PASS"|"FAIL","violations":["..."]}.
                Use PASS when the plan has a goal, start situation, playable progression, transition or completion conditions, and at least one ending.
                Check map usage per stage. A stage marked REQUIRED must contain an exact supplied mapDefinitionId, assetId, and assetLocator from the same map entry. OPTIONAL and NONE stages may omit map references. Do not infer that every dungeon or exploration stage requires a map.
                For triggers and checks, first decide whether a stage actually needs one. A stage without hidden information, a conditional event, or a rules check may have no trigger and still PASS.
                When a trigger or check is needed, verify only that its activation condition, check (if any), and resulting outcome are usable, and that explicitly evidenced core triggers or checks were not omitted.
                Every hidden-information trigger, secret, clue reveal, conditional event, or rules check that is present MUST have an explicit success result and an explicit failure or fail-forward consequence. A trigger with only one outcome is unusable and must produce a concise violation naming the affected stage.
                Do not fail for heading names, Markdown formatting, stage count wording, prose style, optional details, or reasonable additions not contradicted by evidence.
                Fail only for missing essential information, an unusable required trigger/check, a missing required map reference, or a clear contradiction with supplied evidence.
                violations must contain concise actionable descriptions. Return an empty array only for PASS.
                configuration=%s
                sourceDocuments=%s
                resolutionEvidence=%s
                maps=%s
                citations=%s
                generatedMarkdown=
                %s
                """.formatted(configuration, request.sourceDocuments(), request.resolutionEvidence(), request.maps(), request.citations(), generatedMarkdown);
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
                Inspect only whether essential information is present and usable against the supplied source evidence, citations, map contract, and configuration.
                Do not rewrite, summarize, extract, or normalize the plan. Do not return stages or any other plan data.
                Return ONLY one JSON object with exactly these fields: {"status":"PASS"|"FAIL","violations":["..."]}.
                Use PASS when the plan has a goal, start situation, playable progression, transition or completion conditions, and at least one ending.
                Check map usage per stage. A stage marked REQUIRED must have an exact supplied map reference; OPTIONAL and NONE may omit one. Do not require maps solely because a stage is a dungeon or exploration scene.
                A stage without hidden information, a conditional event, or a rules check may have no trigger and still PASS.
                When a trigger or check is needed, verify only that its activation condition, check (if any), and outcome are usable.
                Every hidden-information trigger, secret, clue reveal, conditional event, or rules check that is present MUST have an explicit success result and an explicit failure or fail-forward consequence. A trigger with only one outcome is unusable and must produce a concise violation naming the affected stage.
                Do not fail for heading names, Markdown formatting, stage count wording, prose style, or optional details.
                Fail only for missing essential information, an unusable required trigger/check, a missing required map reference, or a clear contradiction.
                violations must contain concise, actionable Korean or English descriptions. Return an empty array only for PASS.
                configuration=%s
                sourceDocuments=%s
                resolutionEvidence=%s
                maps=%s
                citations=%s
                generatedMarkdown=
                %s
                """.formatted(configuration, request.sourceDocuments(), request.resolutionEvidence(), request.maps(), request.citations(), request.generatedMarkdown());
        try {
            String response = complete(endpoint, request.operationId(), prompt, configuration);
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
        try {
            JsonNode root = mapper.readTree(extractObject(text));
            JsonNode stages = root.get("stages");
            if (stages == null || !stages.isArray()) throw new IllegalArgumentException("stages missing");
            List<Stage> result = new ArrayList<>();
            for (JsonNode n : stages) {
                if (!n.isObject()) throw new IllegalArgumentException("stage must be an object");
                JsonNode position = n.get("position");
                if (position == null || !position.isIntegralNumber()) throw new IllegalArgumentException("position missing");
                List<String> endings = strings(n.get("endingIds"), "endingIds");
                if (endings.isEmpty()) throw new IllegalArgumentException("endingIds must be explicit");
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
            String field = normalized.contains("transitioncondition") ? "stages[*].transitionCondition"
                    : normalized.contains("clearcondition") ? "stages[*].clearCondition"
                    : normalized.contains("failurecondition") ? "stages[*].failureCondition"
                    : normalized.contains("citation") ? "stages[*].evidence[*].citationKey" : "stages";
            if (stagePosition != null && field.startsWith("stages[*].")) {
                field = field.replace("stages[*]", "stages[" + (stagePosition - 1) + "]");
            }
            String code = normalized.contains("citation") ? "CITATION_CONTRACT_VIOLATION" : "PROJECTION_FIELD_INVALID";
            ProjectionViolation.Repairability repairability = normalized.contains("citation")
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
            String previousCandidate, String generationMode, JsonNode sourceConstraintPack) {
        public Request(String operationId, long packageRevision, int partySize, Configuration configuration, List<String> sourceDocuments,
                List<String> resolutionEvidence, List<MapContext> maps, List<SourceCitation> citations, List<String> violations,
                String previousCandidate) {
            this(operationId, packageRevision, partySize, configuration, sourceDocuments, resolutionEvidence, maps, citations,
                    violations, previousCandidate, "GENERATIVE", null);
        }
        public Request(String operationId, long packageRevision, int partySize, Configuration configuration,
                List<String> sourceDocuments, List<String> resolutionEvidence,
                List<MapContext> maps, List<SourceCitation> citations) {
            this(operationId, packageRevision, partySize, configuration, sourceDocuments,
                    resolutionEvidence, maps, citations, List.of(), "");
        }
        public Request(String operationId, long packageRevision, int partySize, Configuration configuration, List<String> sourceDocuments, List<String> resolutionEvidence) {
            this(operationId, packageRevision, partySize, configuration, sourceDocuments,
                    resolutionEvidence, List.of(), List.of(), List.of(), "");
        }
        public Request {
            violations = violations == null ? List.of() : List.copyOf(violations);
            previousCandidate = previousCandidate == null ? "" : previousCandidate;
            generationMode = generationMode == null || generationMode.isBlank() ? "GENERATIVE" : generationMode;
        }
    }
    public record VerificationRequest(String operationId, Configuration configuration, List<String> sourceDocuments,
            List<String> resolutionEvidence, List<MapContext> maps, List<SourceCitation> citations, String generatedMarkdown) {
        public VerificationRequest {
            sourceDocuments = sourceDocuments == null ? List.of() : List.copyOf(sourceDocuments);
            resolutionEvidence = resolutionEvidence == null ? List.of() : List.copyOf(resolutionEvidence);
            maps = maps == null ? List.of() : List.copyOf(maps);
            citations = citations == null ? List.of() : List.copyOf(citations);
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
            RepairScope repairScope) {
        public RepairRequest(String operationId, long packageRevision, int partySize, Configuration configuration,
                JsonNode previousCandidate, List<ProjectionViolation> violations, List<String> sourceDocuments,
                List<String> resolutionEvidence, List<MapContext> maps, List<SourceCitation> citations) {
            this(operationId, packageRevision, partySize, configuration, previousCandidate, violations, sourceDocuments,
                    resolutionEvidence, maps, citations, RepairScope.from(violations));
        }
        public RepairRequest {
            violations = violations == null ? List.of() : List.copyOf(violations);
            sourceDocuments = sourceDocuments == null ? List.of() : List.copyOf(sourceDocuments);
            resolutionEvidence = resolutionEvidence == null ? List.of() : List.copyOf(resolutionEvidence);
            maps = maps == null ? List.of() : List.copyOf(maps);
            citations = citations == null ? List.of() : List.copyOf(citations);
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
    public record Response(List<Stage> stages) {}
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
