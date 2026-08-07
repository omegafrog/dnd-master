package com.dndmaster.aigamemaster.api;

import com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionAdapter;
import com.dndmaster.aigamemaster.infrastructure.ai.GmProviderRequest;
import com.dndmaster.aigamemaster.infrastructure.ai.DeadlineBudget;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/** Provider-neutral read-only GM loop. No tool calls or state mutations are exposed. */
@RestController
public final class GmAgentController {
    private final GmCompletionAdapter adapter;
    private final ObjectMapper mapper;
    private final ApiRequestGuard requestGuard;
    private final java.time.Duration totalTimeout;
    private final java.time.Duration retrievalTimeout;

    public GmAgentController(GmCompletionAdapter adapter, ObjectMapper mapper, ApiRequestGuard requestGuard) {
        this(adapter, mapper, requestGuard, java.time.Duration.ofSeconds(90), java.time.Duration.ofSeconds(30));
    }

    @Autowired
    public GmAgentController(GmCompletionAdapter adapter, ObjectMapper mapper, ApiRequestGuard requestGuard,
                             @Value("${ai.gm.timeout:90s}") java.time.Duration totalTimeout,
                             @Value("${ai.gm.retrieval-timeout:30s}") java.time.Duration retrievalTimeout) {
        this.adapter = adapter;
        this.mapper = mapper;
        this.requestGuard = requestGuard;
        this.totalTimeout = totalTimeout;
        this.retrievalTimeout = retrievalTimeout;
    }

    @PostMapping("/internal/v1/gm/agent-turns")
    Response plan(@RequestHeader(value = "X-Internal-Token", required = false) String token, @RequestBody Request request) {
        requestGuard.internal(token);
        if (request == null || request.action() == null || request.action().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "action required");
        }
        String operation = request.operationKey();
        DeadlineBudget budget = DeadlineBudget.start(totalTimeout, retrievalTimeout);
        try {
                return complete(request, operation, prompt(request), request.protectedFacts(), budget);
        } catch (com.dndmaster.aigamemaster.infrastructure.ai.ProviderMalformedResponseException malformed) {
            try {
                return complete(request, operation + ":repair", repairPrompt(request, request.protectedFacts()), request.protectedFacts(), budget);
            } catch (RuntimeException stillMalformed) {
                throw failure(operation, stillMalformed);
            }
        } catch (RuntimeException providerFailure) {
            throw failure(operation, providerFailure);
        }
    }

    @ExceptionHandler(GmAgentFailureException.class)
    org.springframework.http.ResponseEntity<GmFailure> handleFailure(GmAgentFailureException exception) {
        return org.springframework.http.ResponseEntity.status(exception.getStatusCode()).body(exception.failure());
    }

    private static GmAgentFailureException failure(String operation, RuntimeException cause) {
        GmFailureCategory category = cause instanceof com.dndmaster.aigamemaster.infrastructure.ai.ProviderTimeoutException
                ? GmFailureCategory.PROVIDER_TIMEOUT
                : cause instanceof GmGroundingViolationException
                ? GmFailureCategory.GROUNDING
                : cause instanceof com.dndmaster.aigamemaster.infrastructure.ai.ProviderMalformedResponseException
                ? GmFailureCategory.SCHEMA : GmFailureCategory.DEPENDENCY;
        String message = switch (category) {
            case PROVIDER_TIMEOUT -> "게임 마스터 응답 시간이 초과되었습니다. 다시 시도해 주세요.";
            case SCHEMA -> "게임 마스터 응답을 확인하지 못했습니다. 다시 시도해 주세요.";
            case GROUNDING -> "확인된 근거가 없어 턴을 완료하지 못했습니다.";
            case CONCURRENCY -> "다른 턴이 처리 중입니다. 잠시 후 다시 시도해 주세요.";
            case DEPENDENCY -> "게임 마스터를 일시적으로 사용할 수 없습니다. 다시 시도해 주세요.";
        };
        return new GmAgentFailureException(new GmFailure(category, true, message,
                operation == null || operation.isBlank() ? UUID.randomUUID().toString() : operation), cause);
    }

    private Response complete(Request request, String operation, String prompt, List<String> protectedFacts,
                              DeadlineBudget budget) {
        if (request.provider() == null || request.provider().isBlank()) {
            return adapter.complete(operation, prompt, json -> parseCompleteResponse(json, protectedFacts, request), budget);
        }
        return adapter.complete(operation, prompt, json -> parseCompleteResponse(json, protectedFacts, request),
                new GmProviderRequest(request.provider(), request.model(), request.reasoning()), budget);
    }

    private Response parseCompleteResponse(String json, List<String> protectedFacts, Request request) {
            try {
                Response response = mapper.readValue(json, Response.class);
                if (response.proposedActiveSourceContext() instanceof String source
                        && source.isBlank()) {
                    response = new Response(response.scene(), response.npcState(), response.judgment(),
                            response.narration(), response.narrationSegments(), null, response.citedEvidence(), response.warnings(),
                            response.provider(), response.model(), response.reasoning(), response.stateDelta(),
                            response.toolCalls());
                }
                response = requireComplete(response);
                response = publicNarration(response);
                validateCitations(response, request);
                try {
                    GmResponseSafetyPolicy.rejectProtectedFacts(response.scene() + " " + response.npcState() + " "
                            + response.judgment() + " " + response.narration(), protectedFacts);
                } catch (IllegalArgumentException violation) {
                    throw new GmGroundingViolationException("GM response failed grounding safety policy");
                }
                return response;
            } catch (GmGroundingViolationException violation) {
                throw violation;
            } catch (Exception exception) {
                throw new com.dndmaster.aigamemaster.infrastructure.ai.ProviderMalformedResponseException(
                        "GM structured response invalid: " + exception.getMessage());
            }
    }

    private static String repairPrompt(Request r, List<String> protectedFacts) {
        return """
                Return exactly one JSON object and no markdown.
                Required keys: scene, npcState, judgment, narration, narrationSegments, proposedActiveSourceContext, citedEvidence, warnings, provider, model, reasoning, stateDelta, toolCalls.
                Use non-null strings for scene, npcState, judgment, narration, provider, model and reasoning.
                Use [] for citedEvidence, warnings, stateDelta and toolCalls; use null for proposedActiveSourceContext.
                citedEvidence MUST be [] unless an evidence object is copied exactly from the supplied request.
                narrationSegments MUST contain objects with visibility PLAYER_VISIBLE or GM_ONLY and text. Put private facts only in GM_ONLY.
                stateDelta MUST be []. Do not output a citation string.
                Do not make rule claims or invent facts. The player action is: %s
                Current scene: %s
                """.formatted(redact(r.action(), protectedFacts), redact(r.currentScene(), protectedFacts));
    }

    private static String redact(String value, List<String> protectedFacts) {
        String redacted = value == null ? "" : value;
        for (String fact : protectedFacts) {
            if (fact == null || fact.isBlank()) continue;
            for (String part : fact.split("\\s+")) {
                if (part.length() >= 4) {
                    redacted = redacted.replaceAll("(?i)" + java.util.regex.Pattern.quote(part), "[REDACTED]");
                }
            }
        }
        return redacted;
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

    private static String prompt(Request r) {
        return """
                SYSTEM: You are a read-only game master. Use only supplied locked evidence and context.
                Never reveal hidden data. Never invent rules, rolls, or state changes.
                Return JSON only with fields scene,npcState,judgment,narration,proposedActiveSourceContext,
                citedEvidence,warnings,provider,model,reasoning,stateDelta,toolCalls. stateDelta MUST be [] .
                narrationSegments is an array of {visibility,text}; visibility is PLAYER_VISIBLE or GM_ONLY. Only PLAYER_VISIBLE text may be shown to the player.
                toolCalls may contain only dice.roll or character.update; each call has toolName,argumentsJson,required.
                Every rule claim needs a citation from supplied evidence.
                citedEvidence is an array of exact evidence objects, never strings. If you cannot copy an evidence object
                exactly, return citedEvidence as []. Do not cite or reproduce hidden DCs, secret locations, or private facts.
                Use this exact JSON shape (replace values; keep array/object types):
                {"scene":"...","npcState":"...","judgment":"...","narration":"...","narrationSegments":[{"visibility":"PLAYER_VISIBLE","text":"..."}],
                "proposedActiveSourceContext":null,"citedEvidence":[],"warnings":[],"provider":"ollama",
                "model":"...","reasoning":"...","stateDelta":[],"toolCalls":[]}
                adventureId=%s packageId=%s bindingVersion=%s action=%s
                currentScene=%s npcState=%s pendingAction=%s latestJudgment=%s
                storybook=%s rulebook=%s resolution=%s recentTurns=%s characters=%s storyPlan=%s
                """.formatted(r.adventureId(), r.scenarioPackageId(), r.bindingVersion(), r.action(), r.currentScene(),
                r.npcState(), r.pendingAction(), r.latestJudgment(), r.storybook(), r.rulebook(), r.resolution(), r.recentTurns(),
                r.characterSnapshots(), r.storyPlanContext());
    }

    void validateCitations(Response response, Request request) {
        List<?> selected = java.util.stream.Stream.of(request.storybook(), request.rulebook(), request.resolution())
                .flatMap(List::stream).toList();
        for (Object citation : response.citedEvidence()) {
            if (!(citation instanceof java.util.Map<?, ?> cited) || selected.stream().noneMatch(item -> sameEvidence(item, cited))) {
                throw new GmGroundingViolationException("citation is outside selected evidence");
            }
        }
    }

    private static boolean sameEvidence(Object selected, java.util.Map<?, ?> cited) {
        if (!(selected instanceof java.util.Map<?, ?> source)) return false;
        return exact(source, cited, "type")
                && exact(source, cited, "knowledgeDocumentId")
                && exact(source, cited, "extractionVersion")
                && exact(source, cited, "locator")
                && exact(source, cited, "excerpt");
    }

    private static boolean exact(java.util.Map<?, ?> left, java.util.Map<?, ?> right, String key) {
        Object a = left.get(key);
        Object b = right.get(key);
        return a != null && b != null && String.valueOf(a).equals(String.valueOf(b));
    }

    public record Request(String operationKey, UUID adventureId, UUID ownerPlayerId, UUID sessionId, UUID turnId, UUID scenarioPackageId, long bindingVersion, String turnCapability, String action,
                          String currentScene, String npcState, String pendingAction, String latestJudgment,
                          List<?> storybook, List<?> rulebook, List<?> resolution, List<String> recentTurns,
                          List<String> characterSnapshots, String storyPlanContext, String provider, String model,
                          String reasoning, List<String> protectedFacts) {
        public Request {
            protectedFacts = protectedFacts == null ? List.of() : List.copyOf(protectedFacts);
        }

        public Request(String operationKey, UUID adventureId, UUID ownerPlayerId, UUID sessionId, UUID turnId,
                       UUID scenarioPackageId, long bindingVersion, String turnCapability, String action,
                       String currentScene, String npcState, String pendingAction, String latestJudgment,
                       List<?> storybook, List<?> rulebook, List<?> resolution, List<String> recentTurns,
                       List<String> characterSnapshots, String storyPlanContext, String provider, String model,
                       String reasoning) {
            this(operationKey, adventureId, ownerPlayerId, sessionId, turnId,
                    scenarioPackageId, bindingVersion, turnCapability, action, currentScene, npcState, pendingAction,
                    latestJudgment, storybook, rulebook, resolution, recentTurns, characterSnapshots, storyPlanContext,
                    provider, model, reasoning, List.of());
        }
    }

    public record CompactionRequest(UUID sessionId, UUID sourceTurnId, String context, Object exactTail, Object snapshotReferences) {}

    public record CompactionResponse(String summary, List<String> unresolvedThreats, UUID planRevisionId, long planVersion) {}

    public static Response requireComplete(Response response) {
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

    private static Response publicNarration(Response response) {
        List<NarrationSegment> segments = response.narrationSegments() == null || response.narrationSegments().isEmpty()
                ? List.of(new NarrationSegment("PLAYER_VISIBLE", response.narration())) : response.narrationSegments();
        segments.forEach(segment -> {
            if (segment == null || segment.text() == null || segment.text().isBlank()
                    || (!"PLAYER_VISIBLE".equals(segment.visibility()) && !"GM_ONLY".equals(segment.visibility()))) {
                throw new com.dndmaster.aigamemaster.infrastructure.ai.ProviderMalformedResponseException("invalid narration segment");
            }
        });
        String narration = segments.stream().filter(segment -> "PLAYER_VISIBLE".equals(segment.visibility()))
                .map(NarrationSegment::text).reduce("", (left, right) -> left.isBlank() ? right : left + " " + right);
        if (narration.isBlank()) throw new com.dndmaster.aigamemaster.infrastructure.ai.ProviderMalformedResponseException("player narration is empty");
        return new Response(response.scene(), response.npcState(), response.judgment(), narration, segments,
                response.proposedActiveSourceContext(), response.citedEvidence(), response.warnings(), response.provider(),
                response.model(), response.reasoning(), response.stateDelta(), response.toolCalls());
    }

    private static void requireText(String value, String name) {
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }

    public record Response(String scene, String npcState, String judgment, String narration, List<NarrationSegment> narrationSegments, Object proposedActiveSourceContext,
                           List<?> citedEvidence, List<String> warnings, String provider, String model, String reasoning,
                           List<String> stateDelta, List<ToolCall> toolCalls) {
        public Response(String scene, String npcState, String judgment, String narration, Object proposedActiveSourceContext,
                        List<?> citedEvidence, List<String> warnings, String provider, String model, String reasoning,
                        List<String> stateDelta, List<ToolCall> toolCalls) {
            this(scene, npcState, judgment, narration, List.of(new NarrationSegment("PLAYER_VISIBLE", narration)),
                    proposedActiveSourceContext, citedEvidence, warnings, provider, model, reasoning, stateDelta, toolCalls);
        }
        public Response(String scene, String npcState, String judgment, String narration, Object proposedActiveSourceContext,
                        List<?> citedEvidence, List<String> warnings, String provider, String model, String reasoning,
                        List<String> stateDelta) {
            this(scene, npcState, judgment, narration, proposedActiveSourceContext, citedEvidence, warnings, provider, model, reasoning, stateDelta, List.of());
        }
        public record ToolCall(String toolName, String argumentsJson, boolean required) {}
    }

    public record NarrationSegment(String visibility, String text) {}
}
