package com.dndmaster.aigamemaster.api;

import com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionAdapter;
import com.dndmaster.aigamemaster.infrastructure.ai.GmProviderRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Provider-neutral read-only GM loop. No tool calls or state mutations are exposed. */
@RestController
public final class GmAgentController {
    private final GmCompletionAdapter adapter;
    private final ObjectMapper mapper;
    private final ApiRequestGuard requestGuard;

    public GmAgentController(GmCompletionAdapter adapter, ObjectMapper mapper, ApiRequestGuard requestGuard) {
        this.adapter = adapter;
        this.mapper = mapper;
        this.requestGuard = requestGuard;
    }

    @PostMapping("/internal/v1/gm/agent-turns")
    Response plan(@RequestHeader(value = "X-Internal-Token", required = false) String token, @RequestBody Request request) {
        requestGuard.internal(token);
        if (request == null || request.action() == null || request.action().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "action required");
        }
        String operation = request.operationKey();
        try {
            return canonicalizeProviderMetadata(request, complete(request, operation, prompt(request)));
        } catch (com.dndmaster.aigamemaster.infrastructure.ai.ProviderMalformedResponseException malformed) {
            try {
                return canonicalizeProviderMetadata(request, complete(request, operation + ":repair", repairPrompt(request)));
            } catch (RuntimeException stillMalformed) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "GM provider structured response unavailable: " + stillMalformed.getMessage(), stillMalformed);
            }
        } catch (RuntimeException providerFailure) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "GM provider unavailable: " + providerFailure.getMessage(), providerFailure);
        }
    }

    private static Response canonicalizeProviderMetadata(Request request, Response response) {
        if (request.provider() == null || request.provider().isBlank()) return response;
        return new Response(response.scene(), response.npcState(), response.judgment(), response.narration(),
                response.proposedActiveSourceContext(), response.citedEvidence(), response.warnings(),
                request.provider(), request.model(), request.reasoning(), response.stateDelta(), response.toolCalls(),
                response.advanceStoryPlan(), response.selectedBranchId());
    }

    private Response complete(Request request, String operation, String prompt) {
        if (request.provider() == null || request.provider().isBlank()) {
            return adapter.complete(operation, prompt, this::parseCompleteResponse);
        }
        return adapter.complete(operation, prompt, this::parseCompleteResponse,
                new GmProviderRequest(request.provider(), request.model(), request.reasoning()));
    }

    private Response parseCompleteResponse(String json) {
            try {
                // Luna occasionally emits an empty object for the read-only state
                // delta and a structured object for npcState/advanceStoryPlan.
                // Normalize those representation-only variants before applying the
                // canonical contract; non-empty state deltas remain rejected.
                com.fasterxml.jackson.databind.node.ObjectNode normalized =
                        (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(json);
                com.fasterxml.jackson.databind.JsonNode npcState = normalized.get("npcState");
                if (npcState != null && !npcState.isTextual() && !npcState.isNull()) {
                    normalized.put("npcState", mapper.writeValueAsString(npcState));
                }
                normalizeArrayField(normalized, "stateDelta");
                normalizeArrayField(normalized, "toolCalls");
                // Tool calls from the free-form provider are not executable until a
                // typed command is explicitly submitted; discard malformed calls here.
                normalized.putArray("toolCalls");
                normalizeArrayField(normalized, "citedEvidence");
                normalizeArrayField(normalized, "warnings");
                com.fasterxml.jackson.databind.JsonNode citations = normalized.get("citedEvidence");
                if (citations.isArray() && java.util.stream.StreamSupport.stream(citations.spliterator(), false)
                        .anyMatch(item -> !validCitation(item))) {
                    normalized.putArray("citedEvidence");
                    ((com.fasterxml.jackson.databind.node.ArrayNode) normalized.get("warnings"))
                            .add("Provider citation format was invalid; unsupported citations were discarded.");
                }
                com.fasterxml.jackson.databind.JsonNode active = normalized.get("proposedActiveSourceContext");
                if (active != null && !active.isObject()) {
                    normalized.putNull("proposedActiveSourceContext");
                } else if (active != null && active.isObject()
                        && (active.path("knowledgeDocumentId").isMissingNode()
                        || active.path("locator").asText().isBlank()
                        || active.path("excerpt").asText().isBlank())) {
                    normalized.putNull("proposedActiveSourceContext");
                }
                com.fasterxml.jackson.databind.JsonNode advancePlan = normalized.get("advanceStoryPlan");
            if (advancePlan == null || advancePlan.isNull()) normalized.put("advanceStoryPlan", false);
            // Branch transitions are committed only by the deterministic runtime after it
            // validates a known branch id. The free-form GM adapter must never request an
            // unverified transition (the plan context can be abbreviated in the prompt).
                if (advancePlan != null && advancePlan.isObject()) {
                    normalized.put("advanceStoryPlan", true);
                }
                // Never trust a free-form branch object without deterministic validation.
                normalized.put("advanceStoryPlan", false);
                normalized.put("selectedBranchId", "");
                if (!normalized.has("scene") || normalized.get("scene").isNull()
                        || normalized.get("scene").asText().isBlank()) normalized.put("scene", "current");
                if (!normalized.has("judgment") || normalized.get("judgment").isNull()
                        || normalized.get("judgment").asText().isBlank()) {
                    normalized.put("judgment", "The action is unresolved; the scene awaits the next meaningful choice.");
                    ((com.fasterxml.jackson.databind.node.ArrayNode) normalized.get("warnings"))
                            .add("Provider omitted a judgment; a neutral judgment was applied.");
                }
                if (!normalized.has("narration") || normalized.get("narration").isNull()
                        || normalized.get("narration").asText().isBlank()) {
                    normalized.put("narration", "The scene holds, awaiting your next decision.");
                    ((com.fasterxml.jackson.databind.node.ArrayNode) normalized.get("warnings"))
                            .add("Provider omitted narration; a neutral narration was applied.");
                }
                if (!normalized.has("provider") || normalized.get("provider").isNull()
                        || normalized.get("provider").asText().isBlank()) normalized.put("provider", "codex-cli");
                if (!normalized.has("model") || normalized.get("model").isNull()
                        || normalized.get("model").asText().isBlank()) normalized.put("model", "gpt-5.6-luna");
                if (!normalized.has("reasoning") || normalized.get("reasoning").isNull()
                        || normalized.get("reasoning").asText().isBlank()) normalized.put("reasoning", "none");
                Response response = mapper.treeToValue(normalized, Response.class);
                if (response.proposedActiveSourceContext() instanceof String source
                        && source.isBlank()) {
                    response = new Response(response.scene(), response.npcState(), response.judgment(),
                            response.narration(), null, response.citedEvidence(), response.warnings(),
                            response.provider(), response.model(), response.reasoning(), response.stateDelta(),
                            response.toolCalls(), response.advanceStoryPlan(), response.selectedBranchId());
                }
                return requireComplete(response);
            } catch (Exception exception) {
                throw new com.dndmaster.aigamemaster.infrastructure.ai.ProviderMalformedResponseException(
                        "GM structured response invalid: " + exception.getMessage());
            }
    }

    private static void normalizeArrayField(com.fasterxml.jackson.databind.node.ObjectNode node, String field) {
        com.fasterxml.jackson.databind.JsonNode value = node.get(field);
        if (value == null || value.isNull() || (value.isTextual() && value.asText().isBlank())
                || (value.isObject() && value.size() == 0)) {
            node.putArray(field);
        }
    }

    private static boolean validCitation(com.fasterxml.jackson.databind.JsonNode item) {
        if (item == null || !item.isObject()
                || !item.path("type").isTextual() || item.path("type").asText().isBlank()
                || !supportedEvidenceType(item.path("type").asText())
                || !item.hasNonNull("knowledgeDocumentId")
                || !item.path("extractionVersion").canConvertToLong()
                || !item.path("locator").isTextual() || item.path("locator").asText().isBlank()
                || !item.path("excerpt").isTextual() || item.path("excerpt").asText().isBlank()) return false;
        try { java.util.UUID.fromString(item.path("knowledgeDocumentId").asText()); return true; }
        catch (IllegalArgumentException ignored) { return false; }
    }

    private static boolean supportedEvidenceType(String value) {
        return "STORYBOOK".equals(value) || "RULEBOOK".equals(value) || "RESOLUTION".equals(value);
    }

    private static String repairPrompt(Request r) {
        return """
                Return exactly one JSON object and no markdown.
                Required keys: scene, npcState, judgment, narration, proposedActiveSourceContext, citedEvidence, warnings, provider, model, reasoning, stateDelta, toolCalls.
                Use non-null strings for scene, judgment, narration; use [] for all arrays and null for proposedActiveSourceContext.
                Do not make rule claims or invent facts. The player action is: %s
                Current scene: %s
                """.formatted(r.action(), r.currentScene());
    }

    @PostMapping("/internal/v1/gm/context-compactions")
    CompactionResponse compact(@RequestHeader(value = "X-Internal-Token", required = false) String token,
                               @RequestBody CompactionRequest request) {
        requestGuard.internal(token);
        if (request == null || request.context() == null || request.context().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "context required");
        }
        return adapter.complete("context-compaction:" + request.sessionId() + ":" + request.sourceTurnId(), compactionPrompt(request), json -> {
            try {
                CompactionResponse response = mapper.readValue(json, CompactionResponse.class);
                if (response.summary() == null || response.summary().isBlank()
                        || response.unresolvedThreats() == null || response.planRevisionId() == null || response.planVersion() < 1) {
                    throw new IllegalArgumentException("summary, threats, planRevisionId and planVersion required");
                }
                return response;
            } catch (Exception exception) {
                throw new com.dndmaster.aigamemaster.infrastructure.ai.ProviderMalformedResponseException(
                        "GM compaction response invalid: " + exception.getMessage());
            }
        });
    }

    @PostMapping("/internal/v1/gm/companion-candidates")
    CompanionCandidateResponse companionCandidate(@RequestHeader(value = "X-Internal-Token", required = false) String token,
                                                   @RequestBody CompanionCandidateRequest request) {
        requestGuard.internal(token);
        if (request == null || request.sessionId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId required");
        try {
            var parser = (com.dndmaster.aigamemaster.infrastructure.ai.StructuredResponseParser<CompanionCandidateResponse>) json -> {
                try {
                    CompanionCandidateResponse result = mapper.readValue(json, CompanionCandidateResponse.class);
                    if (blank(result.name()) || blank(result.race()) || blank(result.characterClass()) || blank(result.sheetSummary())) {
                        throw new IllegalArgumentException("candidate fields required");
                    }
                    return result;
                } catch (Exception exception) { throw new com.dndmaster.aigamemaster.infrastructure.ai.ProviderMalformedResponseException("companion candidate invalid"); }
            };
            return request.provider() == null || request.provider().isBlank()
                    ? adapter.complete("companion-candidate:" + request.sessionId() + ":" + UUID.randomUUID(), companionPrompt(request), parser)
                    : adapter.complete("companion-candidate:" + request.sessionId() + ":" + UUID.randomUUID(), companionPrompt(request), parser,
                            new GmProviderRequest(request.provider(), request.model(), request.reasoning()));
        } catch (RuntimeException failure) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "GM companion candidate unavailable", failure); }
    }

    @PostMapping("/internal/v1/gm/quality-evaluation")
    GmQualityEvaluationService.EvaluationReport evaluateQuality(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody List<GmQualityEvaluationService.Scenario> scenarios) {
        requestGuard.internal(token);
        return new GmQualityEvaluationService(adapter, mapper).evaluateReport(scenarios);
    }

    private static String compactionPrompt(CompactionRequest r) {
        return """
                SYSTEM: Compact GM context for internal resume only. Preserve canonical facts as references, not replacements.
                Return JSON only with summary, unresolvedThreats, planRevisionId, planVersion.
                Do not copy or rewrite exactTail. Do not invent state absent from context.
                sessionId=%s sourceTurnId=%s context=%s exactTail=%s snapshotReferences=%s
                """.formatted(r.sessionId(), r.sourceTurnId(), r.context(), r.exactTail(), r.snapshotReferences());
    }
    private static String companionPrompt(CompanionCandidateRequest request) {
        return """
                Return exactly one JSON object and no markdown with name,race,characterClass,sheetSummary.
                Create a legal D&D 5e (2014) level-1 fighter companion. Allowed races: 드워프, 엘프, 하플링, 인간.
                characterClass must be 파이터. Summary must be one concise Korean sentence.
                Do not use copyrighted character names. sessionId=%s
                """.formatted(request.sessionId());
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }

    private static String prompt(Request r) {
        return """
                SYSTEM: You are "마르셀", a seasoned Korean tabletop RPG game master: warm, witty, vivid,
                and attentive to player agency. Speak like a real GM at the table, in natural conversational
                Korean. Address the party directly using polite but relaxed spoken language (합니다체와
                해요체를 자연스럽게 섞되 문어체 보고서처럼 쓰지 말 것). Never phrase a player-facing
                question as an instruction ending in "~해줘", "~하십시오", or "설명해줘". Ask naturally,
                for example: "문을 열어볼까요?", "어떻게 움직이시겠어요?", "누가 먼저 나설까요?"
                Do not steer the player toward one preferred action. Never end with a single suggested
                action such as "병을 치울까요?" or "문을 열어볼까요?". You may end with no question at all,
                or present at least two materially different options (including an open-ended option such as
                "다른 방법을 시도해도 좋습니다") without implying which one is best. When you present
                options, keep them separate from the scene narration using this exact spoken-chat format:
                first write the scene, then a blank line, then "선택지:", followed by a Markdown ordered list
                starting at 1. Never put numbered options inline in a paragraph, and never use bullets for them.
                You are a read-only game master. Use only supplied locked evidence and context.
                Never reveal hidden data. Never invent rules, rolls, or state changes.
                Return JSON only with fields scene,npcState,judgment,narration,proposedActiveSourceContext,
                citedEvidence,warnings,provider,model,reasoning,stateDelta,toolCalls,advanceStoryPlan,selectedBranchId. stateDelta MUST be [] .
                toolCalls may contain only dice.roll or character.update; each call has toolName,argumentsJson,required.
                Every rule claim needs a citation from supplied evidence.
                Ground the turn in at least one supplied storybook item: copy its exact knowledgeDocumentId,
                extractionVersion and locator into citedEvidence. Do not emit an empty citedEvidence when storybook is non-empty.
                Preserve currentScene and unresolved facts across turns. Change scene only when the supplied storybook
                or resolution evidence establishes the transition. Do not end narration with a single player-facing
                recommendation; use multiple choices in a separate ordered list or simply leave the scene open
                for the player's response.
                Treat the player's action as the latest event in the fiction. Do not repeat a stale description as if
                nothing happened: if the player says they pick up, move, close, break, speak to, or otherwise affect
                an object or creature, acknowledge that attempt in the narration. When the outcome is uncertain,
                describe the attempt as pending and ask for the appropriate roll; when no roll is needed, describe
                the resulting state. The current scene must not claim that an object is still in its pre-action state
                after the player has acted on it. Keep the player's action and the GM's response in conversational
                spoken Korean, not as a request to "설명해줘" or a report.
                For story-plan advancement, advanceStoryPlan MUST be false unless the player explicitly completed a
                transition condition. If it is true, selectedBranchId MUST be copied exactly from a branch ID present
                in the supplied storyPlan; never invent branch IDs. If no valid branch ID is visible, keep it false.
                adventureId=%s packageId=%s bindingVersion=%s action=%s
                currentScene=%s npcState=%s pendingAction=%s latestJudgment=%s
                storybook=%s rulebook=%s resolution=%s recentTurns=%s characters=%s storyPlan=%s
                """.formatted(r.adventureId(), r.scenarioPackageId(), r.bindingVersion(), r.action(), r.currentScene(),
                r.npcState(), r.pendingAction(), r.latestJudgment(), r.storybook(), r.rulebook(), r.resolution(), r.recentTurns(),
                r.characterSnapshots(), r.storyPlanContext());
    }

    public record Request(String operationKey, UUID adventureId, UUID ownerPlayerId, UUID sessionId, UUID turnId, UUID scenarioPackageId, long bindingVersion, String turnCapability, String action,
                          String currentScene, String npcState, String pendingAction, String latestJudgment,
                          List<?> storybook, List<?> rulebook, List<?> resolution, List<String> recentTurns,
                          List<String> characterSnapshots, String storyPlanContext, String provider, String model,
                          String reasoning) {}
    public record CompanionCandidateRequest(UUID sessionId, String provider, String model, String reasoning) {}
    public record CompanionCandidateResponse(String name, String race, String characterClass, String sheetSummary) {}

    public record CompactionRequest(UUID sessionId, UUID sourceTurnId, String context, Object exactTail, Object snapshotReferences) {}

    public record CompactionResponse(String summary, List<String> unresolvedThreats, UUID planRevisionId, long planVersion) {}

    static Response requireComplete(Response response) {
        if (response == null || response.scene() == null || response.judgment() == null || response.narration() == null
                || response.citedEvidence() == null || response.warnings() == null || response.provider() == null
                || response.model() == null || response.reasoning() == null || response.stateDelta() == null
                || response.toolCalls() == null) {
            throw new IllegalArgumentException("all structured GM fields are required");
        }
        requireText(response.scene(), "scene");
        requireText(response.judgment(), "judgment");
        requireText(response.narration(), "narration");
        requireText(response.provider(), "provider");
        requireText(response.model(), "model");
        if (!response.stateDelta().isEmpty()) throw new IllegalArgumentException("read-only GM state delta must be empty");
        if (response.toolCalls() != null && response.toolCalls().stream().anyMatch(call -> call == null
                || (!"dice.roll".equals(call.toolName()) && !"character.update".equals(call.toolName())
                || call.argumentsJson() == null))) {
            throw new IllegalArgumentException("unsupported GM tool call");
        }
        return response;
    }

    private static void requireText(String value, String name) {
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }

    public record Response(String scene, String npcState, String judgment, String narration, Object proposedActiveSourceContext,
                           List<?> citedEvidence, List<String> warnings, String provider, String model, String reasoning,
                           List<String> stateDelta, List<ToolCall> toolCalls, boolean advanceStoryPlan, String selectedBranchId) {
        public Response(String scene, String npcState, String judgment, String narration, Object proposedActiveSourceContext,
                        List<?> citedEvidence, List<String> warnings, String provider, String model, String reasoning,
                        List<String> stateDelta) {
            this(scene, npcState, judgment, narration, proposedActiveSourceContext, citedEvidence, warnings, provider, model, reasoning, stateDelta, List.of(), false, "");
        }
        public record ToolCall(String toolName, String argumentsJson, boolean required) {}
    }
}
