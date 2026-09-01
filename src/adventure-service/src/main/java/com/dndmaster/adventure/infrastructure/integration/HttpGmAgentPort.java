package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.runtime.EvidencePack;
import com.dndmaster.adventure.application.runtime.GmAgentPort;
import com.dndmaster.adventure.application.runtime.GmCitationBinding;
import com.dndmaster.adventure.application.runtime.GmContextEnvelope;
import com.dndmaster.adventure.application.runtime.GmPlanResult;
import com.dndmaster.adventure.application.runtime.RuntimeEvidence;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceType;
import com.dndmaster.adventure.application.runtime.RuntimePlan;
import com.dndmaster.adventure.application.runtime.GmToolCall;
import com.dndmaster.adventure.domain.adventure.ActiveSourceContext;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class HttpGmAgentPort implements GmAgentPort {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(HttpGmAgentPort.class);
    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final String internalToken;

    public HttpGmAgentPort(HttpClient client, URI baseUri, Duration timeout, ObjectMapper mapper, String internalToken) {
        this.client = Objects.requireNonNull(client);
        this.baseUri = Objects.requireNonNull(baseUri);
        this.timeout = Objects.requireNonNull(timeout);
        this.mapper = Objects.requireNonNull(mapper);
        this.internalToken = Objects.requireNonNull(internalToken);
    }

    @Override
    public GmPlanResult plan(GmContextEnvelope context) {
        return plan(context, (com.dndmaster.adventure.application.runtime.TurnCapability) null);
    }

    @Override
    public GmPlanResult plan(GmContextEnvelope context, List<com.dndmaster.adventure.application.runtime.GmToolSpec> tools) {
        return plan(context, null, tools);
    }

    @Override
    public GmPlanResult plan(GmContextEnvelope context, com.dndmaster.adventure.application.runtime.TurnCapability capability) {
        return plan(context, capability, List.of());
    }

    @Override
    public GmPlanResult plan(GmContextEnvelope context, com.dndmaster.adventure.application.runtime.TurnCapability capability,
                             List<com.dndmaster.adventure.application.runtime.GmToolSpec> tools) {
        try {
            String body = mapper.writeValueAsString(V2Request.from(context, capability, tools));
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("internal/v2/gm/agent-turns"))
                    .timeout(timeout).header("Content-Type", "application/json")
                    .header("X-Internal-Token", internalToken)
                    .header("Authorization", "Bearer " + context.ownerPlayerId().value())
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) throw new IllegalStateException("GM agent returned " + response.statusCode() + ": " + response.body());
            V2Response dto;
            try {
                dto = mapper.readValue(response.body(), V2Response.class);
            } catch (Exception exception) {
                LOGGER.warn("gm_response_mapping_failed stage=GM_RESPONSE_DESERIALIZATION turnId={} status={} exceptionClass={} exceptionMessage={}",
                        context.turnId(), response.statusCode(), exception.getClass().getSimpleName(), safeMessage(exception));
                throw exception;
            }
            try {
                return V2Response.toResult(dto);
            } catch (Exception exception) {
                LOGGER.warn("gm_response_mapping_failed stage=GM_RESPONSE_DOMAIN_MAPPING turnId={} status={} exceptionClass={} exceptionMessage={}",
                        context.turnId(), response.statusCode(), exception.getClass().getSimpleName(), safeMessage(exception));
                throw exception;
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GM agent interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("GM agent call failed: " + exception.getMessage(), exception);
        }
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null ? "" : message.replaceAll("[\\r\\n]", " ");
    }

    record Request(String operationKey, UUID adventureId, UUID ownerPlayerId, UUID sessionId, UUID turnId, UUID scenarioPackageId, long bindingVersion, String turnCapability,
                   String action, String currentScene, String npcState, String pendingAction, String latestJudgment,
                   List<Evidence> storybook, List<Evidence> rulebook, List<Evidence> resolution, List<String> recentTurns,
                   List<String> characterSnapshots, String storyPlanContext, String provider, String model, String reasoning) {
        static Request from(GmContextEnvelope c, com.dndmaster.adventure.application.runtime.TurnCapability capability) {
            var context = c.currentContext();
            return new Request(c.operationKey(), c.adventureId().value(), c.ownerPlayerId().value(), c.sessionId(), c.turnId(), c.scenarioPackageId(), c.bindingVersion(), capability == null ? null : capability.token(),
                    c.action(), nonNullText(context.currentScene()), nonNullText(context.npcState()), nonNullText(context.pendingAction()), nonNullText(context.latestJudgment()),
                    c.evidencePack().storybook().stream().map(Evidence::from).toList(),
                    c.evidencePack().rulebook().stream().map(Evidence::from).toList(),
                    c.evidencePack().resolution().stream().map(Evidence::from).toList(), c.recentTurns(),
                    c.characterSnapshots(), c.storyPlanContext(), c.provider(), c.model(), c.reasoning());
        }

        private static String nonNullText(String value) {
            return value == null ? "" : value;
        }
    }

    record V2Request(String operationKey, UUID adventureId, UUID ownerPlayerId, UUID sessionId, UUID turnId,
                     UUID scenarioPackageId, long bindingVersion, String turnCapability, String action,
                     String currentScene, String npcState, String pendingAction, String latestJudgment,
                     List<Evidence> storybook, List<Evidence> rulebook, List<Evidence> resolution,
                     List<String> recentTurns, List<String> characterSnapshots, String storyPlanContext,
                     RequestedSelection requestedSelection, List<com.dndmaster.adventure.application.runtime.GmToolSpec> tools) {
        static V2Request from(GmContextEnvelope c, com.dndmaster.adventure.application.runtime.TurnCapability capability,
                              List<com.dndmaster.adventure.application.runtime.GmToolSpec> tools) {
            var context = c.currentContext();
            return new V2Request(c.operationKey(), c.adventureId().value(), c.ownerPlayerId().value(), c.sessionId(), c.turnId(),
                    c.scenarioPackageId(), c.bindingVersion(), capability == null ? null : capability.token(), c.action(),
                    context.currentScene(), context.npcState(), context.pendingAction(), context.latestJudgment(),
                    c.evidencePack().storybook().stream().map(Evidence::from).toList(),
                    c.evidencePack().rulebook().stream().map(Evidence::from).toList(),
                    c.evidencePack().resolution().stream().map(Evidence::from).toList(), c.recentTurns(),
                    c.characterSnapshots(), c.storyPlanContext(),
                    new RequestedSelection(c.requestedSelection().endpointId(), c.requestedSelection().provider(),
                            c.requestedSelection().model(), c.requestedSelection().reasoning()), tools);
        }
    }

    record RequestedSelection(UUID endpointId, String provider, String model, String reasoning) {}
    record EffectiveSelection(UUID endpointId, java.time.Instant endpointVersion, String provider, String model, String reasoning) {
        com.dndmaster.adventure.domain.runtime.EffectiveGmProviderSelection toDomain() {
            return new com.dndmaster.adventure.domain.runtime.EffectiveGmProviderSelection(
                    endpointId, endpointVersion, provider, model, reasoning);
        }
    }

    record V2Response(Response candidate, RequestedSelection requestedSelection,
                      EffectiveSelection effectiveSelection, int attemptCount) {
        static GmPlanResult toResult(V2Response response) {
            GmPlanResult result = Response.toResult(response.candidate(), response.effectiveSelection().provider(),
                    response.effectiveSelection().model(), response.effectiveSelection().reasoning());
            com.dndmaster.adventure.domain.runtime.RequestedGmProviderSelection requested =
                    new com.dndmaster.adventure.domain.runtime.RequestedGmProviderSelection(
                            response.requestedSelection().endpointId(), response.requestedSelection().provider(),
                            response.requestedSelection().model(), response.requestedSelection().reasoning());
            RuntimePlan plan = result.plan();
            RuntimePlan audited = new RuntimePlan(plan.scene(), plan.npcState(), plan.judgment(), plan.narration(),
                    plan.proposedActiveSourceContext(), plan.citedEvidence(), plan.warnings(), response.effectiveSelection().provider(), response.effectiveSelection().model(),
                    response.effectiveSelection().reasoning(), plan.advanceStoryPlan(), plan.selectedBranchId(), requested,
                    response.effectiveSelection().toDomain(), response.attemptCount(), plan.citationBindings(), plan.stateDelta());
            return new GmPlanResult(audited, result.provider(), result.model(), result.reasoning(), result.stateDelta(), result.toolCalls());
        }
    }

    record Evidence(String type, UUID knowledgeDocumentId, long extractionVersion, String locator, String excerpt,
                    String citationKey) {
        static Evidence from(RuntimeEvidence e) {
            return new Evidence(e.evidenceType().name(), e.knowledgeDocumentId().value(), e.extractionVersion(), e.locator(),
                    e.excerpt(), e.citationKey());
        }
        RuntimeEvidence toEvidence() {
            return new RuntimeEvidence(RuntimeEvidenceType.valueOf(type), new KnowledgeDocumentId(knowledgeDocumentId),
                    extractionVersion, locator, excerpt, citationKey);
        }
    }

    record ActiveSource(UUID knowledgeDocumentId, long extractionVersion, String locator, String excerpt) {
        ActiveSourceContext toDomain() { return new ActiveSourceContext(new KnowledgeDocumentId(knowledgeDocumentId), extractionVersion, locator, excerpt); }
    }

    record Response(String scene, String npcState, String judgment, String narration, ActiveSource proposedActiveSourceContext,
                   List<Evidence> citedEvidence, List<String> warnings, String provider, String model, String reasoning, List<String> stateDelta,
                   List<ToolCall> toolCalls, boolean advanceStoryPlan, String selectedBranchId,
                   List<GmCitationBinding> citationBindings) {
        Response {
            citationBindings = citationBindings == null ? List.of() : List.copyOf(citationBindings);
        }
        record ToolCall(String toolName, Object argumentsJson, boolean required) {
            GmToolCall toDomain() {
                try { return new GmToolCall(toolName, new ObjectMapper().writeValueAsString(argumentsJson), required); }
                catch (Exception e) { throw new IllegalStateException("tool arguments serialization failed", e); }
            }
        }
        static GmPlanResult toResult(Response r) {
            return toResult(r, r.provider, r.model, r.reasoning);
        }

        static GmPlanResult toResult(Response r, String provider, String model, String reasoning) {
            if (r.scene == null || r.judgment == null || r.narration == null) {
                throw new IllegalStateException("GM response omitted semantic fields");
            }
            List<Evidence> evidence = r.citedEvidence == null ? List.of() : r.citedEvidence;
            List<String> warnings = r.warnings == null ? List.of() : r.warnings;
            List<RuntimeEvidence> citations = mapCitations(evidence);
            List<GmToolCall> calls = r.toolCalls == null ? List.of() : r.toolCalls.stream().map(ToolCall::toDomain).toList();
            return new GmPlanResult(new RuntimePlan(r.scene, r.npcState, r.judgment, r.narration,
                    r.proposedActiveSourceContext == null ? null : r.proposedActiveSourceContext.toDomain(), citations,
                    warnings, provider, model, reasoning, r.advanceStoryPlan, r.selectedBranchId,
                    r.citationBindings), provider, model, reasoning,
                    r.stateDelta == null ? List.of() : r.stateDelta, calls);
        }

        private static List<RuntimeEvidence> mapCitations(List<Evidence> evidence) {
            java.util.ArrayList<RuntimeEvidence> mapped = new java.util.ArrayList<>();
            for (int index = 0; index < evidence.size(); index++) {
                try {
                    mapped.add(evidence.get(index).toEvidence());
                } catch (RuntimeException exception) {
                    throw new IllegalStateException("citedEvidence[" + index + "] mapping failed: " + exception.getMessage(), exception);
                }
            }
            return List.copyOf(mapped);
        }
    }
}
