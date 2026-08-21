package com.dndmaster.aigamemaster.api;

import com.dndmaster.aigamemaster.infrastructure.ai.SpringAiChatAdapter;
import com.dndmaster.aigamemaster.infrastructure.ai.CodexCliStoryPlanAdapter;
import com.dndmaster.aigamemaster.application.endpoint.AgentEndpoint;
import com.dndmaster.aigamemaster.application.endpoint.AgentEndpointRegistry;
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

/** Generates a source-aware outline. JSON is validated before crossing the service boundary. */
@RestController("aiAdventureStoryPlanController")
public final class AdventureStoryPlanController {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdventureStoryPlanController.class);
    private final SpringAiChatAdapter adapter; private final ObjectMapper mapper; private final AgentEndpointRegistry endpointRegistry;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final URI ollamaBaseUrl; private final String ollamaModel;
    private final String codexExecutable; private final java.nio.file.Path codexWorkDirectory; private final Duration codexTimeout;
    private final ApiRequestGuard requestGuard;
    public AdventureStoryPlanController(SpringAiChatAdapter adapter, ObjectMapper mapper, AgentEndpointRegistry endpointRegistry,
            @Value("${local-ai.ollama.base-url:http://127.0.0.1:11434}") String ollamaBaseUrl,
            @Value("${local-ai.ollama.chat-model:qwen3:8b}") String ollamaModel,
            @Value("${ai.codex.executable:codex}") String codexExecutable,
            @Value("${ai.codex.work-directory:.}") String codexWorkDirectory,
            @Value("${ai.codex.timeout:PT5M}") Duration codexTimeout,
            @Value("${ai-game-master.integration.internal-token:${INTERNAL_SERVICE_TOKEN:}}") String internalToken) {
        this(adapter, mapper, endpointRegistry, ollamaBaseUrl, ollamaModel, codexExecutable, codexWorkDirectory, codexTimeout,
                new ApiRequestGuard(internalToken));
    }
    public AdventureStoryPlanController(SpringAiChatAdapter adapter, ObjectMapper mapper, AgentEndpointRegistry endpointRegistry,
            String ollamaBaseUrl, String ollamaModel, String codexExecutable, String codexWorkDirectory, Duration codexTimeout,
            ApiRequestGuard requestGuard) {
        this.adapter = adapter; this.mapper = mapper; this.endpointRegistry = endpointRegistry; this.ollamaBaseUrl = URI.create(ollamaBaseUrl); this.ollamaModel = ollamaModel;
        this.codexExecutable = codexExecutable; this.codexWorkDirectory = java.nio.file.Path.of(codexWorkDirectory); this.codexTimeout = codexTimeout;
        this.requestGuard = requestGuard;
    }
    @PostMapping("/internal/v1/gm/adventure-story-plan")
    Response generate(@RequestHeader(value = "X-Internal-Token", required = false) String internalToken, @RequestBody Request request) {
        requestGuard.internal(internalToken);
        AgentEndpoint endpoint = endpointRegistry.active();
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
                + "Do not invent named rules, DCs, monsters, or facts absent from evidence. For a check without an evidenced DC, write 'GM adjudication' rather than inventing a number. Include checks only when a trigger exists. Documents=" + request.sourceDocuments()
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
            VerificationResponse verification = parseVerificationResponse(complete(endpoint, request.operationId() + "-verification",
                    verificationDecisionPrompt(request, configuration, generatedMarkdown), configuration));
            LOGGER.warn("ai_agent_verification_result operationId={} status={} violations={}", request.operationId(),
                    verification.status(), safeViolations(verification.violations()));
            if (verification.status().equals("FAIL")) {
                throw new CandidateResponseValidationException(verification.violations(), null);
            }
            String projectedJson = complete(endpoint, request.operationId() + "-execution-projection",
                    projectionPrompt(request, configuration, generatedMarkdown), configuration);
            return new Response(parseJson(projectedJson, configuration));
        } catch (CandidateResponseValidationException invalidCandidate) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, invalidCandidate.getMessage(), invalidCandidate);
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
                .body(new CandidateValidationError(failure.violations()));
    }

    private String complete(AgentEndpoint endpoint, String operationId, String prompt, Configuration configuration) throws IOException, InterruptedException {
        long startedAt = System.nanoTime();
        LOGGER.info("ai_agent_phase_started phase={} provider={} operationId={} promptLength={}", phase(operationId), endpoint.provider(), operationId, prompt.length());
        if (endpoint.provider() == AgentEndpoint.Provider.CODEX_CLI) {
            return new CodexCliStoryPlanAdapter(codexExecutable, endpoint.model(), codexWorkDirectory, codexTimeout)
                    .complete(operationId, prompt);
        }
        URI baseUrl = endpoint.provider() == AgentEndpoint.Provider.OLLAMA ? endpoint.baseUrl() : ollamaBaseUrl;
        String model = endpoint.model().isBlank() ? ollamaModel : endpoint.model();
        int outputTokens = Math.max(4096, configuration.maximumStages() * 900);
        String body = mapper.writeValueAsString(Map.of("model", model, "prompt", prompt,
                "stream", false, "think", false, "options", Map.of("num_predict", outputTokens)));
        HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder(baseUrl.resolve("/api/generate"))
                .timeout(Duration.ofMinutes(10)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("Ollama returned HTTP " + response.statusCode());
        String result = mapper.readTree(response.body()).path("response").asText();
        LOGGER.info("ai_agent_phase_completed phase={} provider={} operationId={} durationMs={} responseLength={}", phase(operationId), endpoint.provider(), operationId,
                java.time.Duration.ofNanos(System.nanoTime() - startedAt).toMillis(), result.length());
        return result;
    }

    private static String phase(String operationId) {
        if (operationId.endsWith("-verification")) return "story-plan-verification";
        if (operationId.endsWith("-execution-projection")) return "story-plan-execution-projection";
        return "story-plan-generation";
    }

    private String projectionPrompt(Request request, Configuration configuration, String generatedMarkdown) {
        return """
                You are the execution projection agent. Read the verified Markdown plan and convert it into the smallest usable execution outline.
                Return ONLY JSON with a stages array. Do not rewrite the narrative or invent facts.
                Each stage MUST include these required fields: position (integer), title, goal, conflict, transitionCondition, endingIds (a non-empty array of strings), and evidence (a non-empty array when supplied citations are available).
                The root object MUST contain stages (an array). Optional fields may be omitted, and additional properties are allowed and ignored.
                JSON shape constraint: {"type":"object","required":["stages"],"properties":{"stages":{"type":"array","items":{"type":"object","required":["position","title","goal","conflict","transitionCondition","endingIds","evidence"],"properties":{"position":{"type":"integer"},"title":{"type":"string"},"goal":{"type":"string"},"conflict":{"type":"string"},"transitionCondition":{"type":"string"},"endingIds":{"type":"array","items":{"type":"string"},"minItems":1},"evidence":{"type":"array","items":{"type":"object","required":["documentType","documentId","extractionVersion","locator","quote","confidence"]},"minItems":1}},"additionalProperties":true}}},"additionalProperties":true}
                The plan configuration requires exactly %s distinct ending IDs. Preserve the explicit ending-1 through ending-%s IDs from the plan; do not omit, merge, rename, or invent ending IDs.
                Include stageType, location, and mapUsage (REQUIRED, OPTIONAL, or NONE) when present. Include mapDefinitionId, mapAssetId, and mapAssetLocator only when mapUsage is REQUIRED; copy all three from the same AVAILABLE MAPS entry. OPTIONAL and NONE may omit them.
                Optional fields may be omitted or empty: npcOrClues, enemies, boss, clearCondition, failureCondition, rewards, branchIds, branchTargets, and player spawn fields. When citations are supplied, evidence is REQUIRED for every stage: copy at least one exact citation object and quote from the supplied citations. A trigger is represented only by a short reference or lookup key; never copy the full trigger or rule text.
                Arrays may be empty arrays and branchTargets may be an empty object. Never invent a map, trigger, citation, enemy, reward, or ending. Use the ending IDs stated in the plan, or a stable structural ending ID when necessary.
                If the plan is invalid, return the best faithful projection so the application can report the violation.
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
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, invalidCandidate.getMessage(), invalidCandidate);
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

    private static List<String> safeViolations(List<String> violations) {
        return violations.stream().map(item -> {
            String normalized = item.replaceAll("\\s+", " ").trim();
            return normalized.length() > 500 ? normalized.substring(0, 500) + "..." : normalized;
        }).toList();
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
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, invalidCandidate.getMessage(), invalidCandidate);
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
                        text(n, "mapDefinitionId", ""), text(n, "mapAssetId", ""), text(n, "mapAssetLocator", ""), optionalStrings(n.get("enemies")), text(n, "boss", ""), text(n, "clearCondition", required(n, "transitionCondition")), text(n, "failureCondition", ""), optionalStrings(n.get("rewards")), optionalStrings(n.get("branchIds")), optionalMaps(n.get("branchTargets")), optionalCitations(n.get("evidence"))));
            }
            if (result.size() < configuration.minimumStages() || result.size() > configuration.maximumStages()) throw new IllegalArgumentException("invalid stage count");
            if (result.stream().flatMap(s -> s.endingIds().stream()).distinct().count() != configuration.endingCount()) throw new IllegalArgumentException("invalid ending count");
            return List.copyOf(result);
        } catch (CandidateResponseValidationException invalidCandidate) {
            throw invalidCandidate;
        } catch (Exception e) {
            throw new CandidateResponseValidationException(rootMessage(e), e);
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
    private static List<SourceCitation> optionalCitations(JsonNode n) {
        return n == null || n.isNull() ? List.of() : citations(n, "evidence");
    }
    private static String text(JsonNode node, String key, String fallback) { String value = node.path(key).asText("").trim(); return value.isBlank() ? fallback : value; }
    private static Map<String, String> maps(JsonNode node, String field) {
        if (node == null || !node.isObject()) throw new IllegalArgumentException(field + " must be explicit");
        Map<String, String> result = new HashMap<>();
        node.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().asText()));
        return Map.copyOf(result);
    }
    private static List<SourceCitation> citations(JsonNode node, String field) {
        if (node == null || !node.isArray()) throw new IllegalArgumentException(field + " must be explicit");
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
    static final class CandidateResponseValidationException extends RuntimeException {
        private final List<String> violations;
        CandidateResponseValidationException(String message, Throwable cause) {
            this(List.of(message), cause);
        }
        CandidateResponseValidationException(List<String> violations, Throwable cause) {
            super(String.join("; ", violations), cause);
            if (violations == null || violations.isEmpty()) throw new IllegalArgumentException("candidate violations must not be empty");
            this.violations = List.copyOf(violations);
        }
        List<String> violations() { return violations; }
    }
    public record Request(String operationId, long packageRevision, int partySize, Configuration configuration, List<String> sourceDocuments,
            List<String> resolutionEvidence, List<MapContext> maps, List<SourceCitation> citations, List<String> violations,
            String previousCandidate) {
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
    public record CandidateValidationError(List<String> violations) {
        public CandidateValidationError { violations = List.copyOf(violations); }
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
