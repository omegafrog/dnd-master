package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.runtime.EvidencePack;
import com.dndmaster.adventure.application.runtime.GmAgentPort;
import com.dndmaster.adventure.application.runtime.GmAgentFailure;
import com.dndmaster.adventure.application.runtime.GmContextEnvelope;
import com.dndmaster.adventure.application.runtime.GmPlanResult;
import com.dndmaster.adventure.application.runtime.RuntimeEvidence;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceType;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceProvenance;
import com.dndmaster.adventure.application.runtime.RuntimePlan;
import com.dndmaster.adventure.application.runtime.StoryEvidenceVisibility;
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
    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final String internalToken;

    public HttpGmAgentPort(HttpClient client, URI baseUri, Duration timeout, ObjectMapper mapper) {
        this(client, baseUri, timeout, mapper, "local-dev-internal-token");
    }

    public HttpGmAgentPort(HttpClient client, URI baseUri, Duration timeout, ObjectMapper mapper, String internalToken) {
        this.client = Objects.requireNonNull(client);
        this.baseUri = Objects.requireNonNull(baseUri);
        this.timeout = Objects.requireNonNull(timeout);
        this.mapper = Objects.requireNonNull(mapper);
        this.internalToken = Objects.requireNonNull(internalToken);
    }

    @Override
    public GmPlanResult plan(GmContextEnvelope context) {
        return plan(context, null);
    }

    @Override
    public GmPlanResult plan(GmContextEnvelope context, com.dndmaster.adventure.application.runtime.TurnCapability capability) {
        try {
            String body = mapper.writeValueAsString(Request.from(context, capability));
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("internal/v1/gm/agent-turns"))
                    .timeout(timeout).header("Content-Type", "application/json")
                    .header("X-Internal-Token", internalToken)
                    .header("Authorization", "Bearer " + context.ownerPlayerId().value())
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) throw failure(response);
            return Response.toResult(mapper.readValue(response.body(), Response.class));
        } catch (GmAgentFailureException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GM agent interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("GM agent call failed", exception);
        }
    }

    private RuntimeException failure(HttpResponse<String> response) {
        try {
            GmAgentFailure failure = mapper.readValue(response.body(), GmAgentFailure.class);
            return new GmAgentFailureException(failure);
        } catch (Exception ignored) {
            return new IllegalStateException("GM agent returned an unusable failure contract");
        }
    }

    record Request(String operationKey, UUID adventureId, UUID ownerPlayerId, UUID sessionId, UUID turnId, UUID scenarioPackageId, long bindingVersion, String turnCapability,
                   String action, String currentScene, String npcState, String pendingAction, String latestJudgment,
                   List<Evidence> storybook, List<Evidence> rulebook, List<Evidence> resolution, List<String> recentTurns,
                   List<String> characterSnapshots, String storyPlanContext, String provider, String model, String reasoning,
                   java.util.Set<String> protectedFacts) {
        static Request from(GmContextEnvelope c, com.dndmaster.adventure.application.runtime.TurnCapability capability) {
            var context = c.currentContext();
            return new Request(c.operationKey(), c.adventureId().value(), c.ownerPlayerId().value(), c.sessionId(), c.turnId(), c.scenarioPackageId(), c.bindingVersion(), capability == null ? null : capability.token(),
                    c.action(), context.currentScene(), context.npcState(), context.pendingAction(), context.latestJudgment(),
                    c.evidencePack().storybook().stream().map(Evidence::from).toList(),
                    c.evidencePack().rulebook().stream().map(Evidence::from).toList(),
                    c.evidencePack().resolution().stream().map(Evidence::from).toList(), c.recentTurns(),
                    c.characterSnapshots(), c.storyPlanContext(), c.provider(), c.model(), c.reasoning(), c.protectedFacts());
        }
    }

    record Evidence(String type, UUID knowledgeDocumentId, long extractionVersion, String locator, String excerpt,
                    StoryEvidenceVisibility visibility, String disclosureEvent, Long disclosureTurn, List<String> context,
                    RuntimeEvidenceProvenance provenance) {
        static Evidence from(RuntimeEvidence e) {
            return new Evidence(e.evidenceType().name(), e.knowledgeDocumentId().value(), e.extractionVersion(), e.locator(), e.excerpt(),
                    e.visibility(), e.disclosureEvent(), e.disclosureTurn(), e.context(), e.provenance());
        }
        RuntimeEvidence toEvidence() {
            return new RuntimeEvidence(RuntimeEvidenceType.valueOf(type), new KnowledgeDocumentId(knowledgeDocumentId), extractionVersion, locator, excerpt,
                    visibility, disclosureEvent, disclosureTurn == null ? 0 : disclosureTurn,
                    context == null ? List.of() : context, provenance);
        }
    }

    record ActiveSource(UUID knowledgeDocumentId, long extractionVersion, String locator, String excerpt) {
        ActiveSourceContext toDomain() { return new ActiveSourceContext(new KnowledgeDocumentId(knowledgeDocumentId), extractionVersion, locator, excerpt); }
    }

    record Response(String scene, String npcState, String judgment, String narration, ActiveSource proposedActiveSourceContext,
                   List<Evidence> citedEvidence, List<String> warnings, String provider, String model, String reasoning, List<String> stateDelta,
                   List<ToolCall> toolCalls) {
        record ToolCall(String toolName, String argumentsJson, boolean required) {
            GmToolCall toDomain() { return new GmToolCall(toolName, argumentsJson, required); }
        }
        static GmPlanResult toResult(Response r) {
            List<RuntimeEvidence> citations = r.citedEvidence == null ? List.of() : r.citedEvidence.stream().map(Evidence::toEvidence).toList();
            if (r.provider == null || r.model == null || r.reasoning == null || r.stateDelta == null
                    || r.citedEvidence == null || r.warnings == null) {
                throw new IllegalStateException("GM response omitted required fields");
            }
            List<GmToolCall> calls = r.toolCalls == null ? List.of() : r.toolCalls.stream().map(ToolCall::toDomain).toList();
            return new GmPlanResult(new RuntimePlan(r.scene, r.npcState, r.judgment, r.narration,
                    r.proposedActiveSourceContext == null ? null : r.proposedActiveSourceContext.toDomain(), citations,
                    r.warnings == null ? List.of() : r.warnings, r.provider, r.model, r.reasoning), r.provider, r.model, r.reasoning,
                    r.stateDelta == null ? List.of() : r.stateDelta, calls);
        }
    }
}
