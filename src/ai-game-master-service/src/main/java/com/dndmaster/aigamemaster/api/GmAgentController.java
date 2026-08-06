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
            return complete(request, operation, prompt(request));
        } catch (com.dndmaster.aigamemaster.infrastructure.ai.ProviderMalformedResponseException malformed) {
            try {
                return complete(request, operation + ":repair", repairPrompt(request));
            } catch (RuntimeException stillMalformed) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "GM provider structured response unavailable", stillMalformed);
            }
        } catch (RuntimeException providerFailure) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "GM provider unavailable", providerFailure);
        }
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
                Response response = mapper.readValue(json, Response.class);
                if (response.proposedActiveSourceContext() instanceof String source
                        && source.isBlank()) {
                    response = new Response(response.scene(), response.npcState(), response.judgment(),
                            response.narration(), null, response.citedEvidence(), response.warnings(),
                            response.provider(), response.model(), response.reasoning(), response.stateDelta(),
                            response.toolCalls());
                }
                return requireComplete(response);
            } catch (Exception exception) {
                throw new com.dndmaster.aigamemaster.infrastructure.ai.ProviderMalformedResponseException(
                        "GM structured response invalid: " + exception.getMessage());
            }
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
                toolCalls may contain only dice.roll or character.update; each call has toolName,argumentsJson,required.
                Every rule claim needs a citation from supplied evidence.
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

    private static void requireText(String value, String name) {
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }

    public record Response(String scene, String npcState, String judgment, String narration, Object proposedActiveSourceContext,
                           List<?> citedEvidence, List<String> warnings, String provider, String model, String reasoning,
                           List<String> stateDelta, List<ToolCall> toolCalls) {
        public Response(String scene, String npcState, String judgment, String narration, Object proposedActiveSourceContext,
                        List<?> citedEvidence, List<String> warnings, String provider, String model, String reasoning,
                        List<String> stateDelta) {
            this(scene, npcState, judgment, narration, proposedActiveSourceContext, citedEvidence, warnings, provider, model, reasoning, stateDelta, List.of());
        }
        public record ToolCall(String toolName, String argumentsJson, boolean required) {}
    }
}
