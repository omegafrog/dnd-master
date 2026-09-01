package com.dndmaster.adventure.application.runtime;

import java.util.Objects;
import java.util.List;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import com.dndmaster.adventure.domain.runtime.RequestedGmProviderSelection;

public final class GmAgentRuntimePlanningAdapter implements RuntimePlanningPort {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(GmAgentRuntimePlanningAdapter.class);
    private final GmAgentPort agentPort;
    private final GmFinalValidator validator;
    private final GmToolGateway gateway;
    private final RuntimeCommandSagaApplicationService saga;
    private final GmFinalizationPort finalizationPort;
    private final java.util.Map<UUID, java.util.List<CandidateGeneration>> pendingCandidates = new java.util.concurrent.ConcurrentHashMap<>();

    public GmAgentRuntimePlanningAdapter(GmAgentPort agentPort, GmFinalValidator validator) {
        this(agentPort, validator, null, null);
    }

    public GmAgentRuntimePlanningAdapter(GmAgentPort agentPort, GmFinalValidator validator,
                                         GmToolGateway gateway, RuntimeCommandSagaApplicationService saga) {
        this(agentPort, validator, gateway, saga, new ReadOnlyGmFinalizationAdapter());
    }

    public GmAgentRuntimePlanningAdapter(GmAgentPort agentPort, GmFinalValidator validator,
                                         GmToolGateway gateway, RuntimeCommandSagaApplicationService saga,
                                         GmFinalizationPort finalizationPort) {
        this.agentPort = Objects.requireNonNull(agentPort);
        this.validator = Objects.requireNonNull(validator);
        this.gateway = gateway;
        this.saga = saga;
        this.finalizationPort = Objects.requireNonNull(finalizationPort);
    }

    @Override
    public RuntimePlan plan(RuntimePlanningRequest request) {
        return planWithOutcomes(request).plan();
    }

    @Override
    public RuntimePlanningResult planWithOutcomes(RuntimePlanningRequest request) {
        return planInternal(request, true);
    }

    /** Gate A candidate-generation path. It preserves tool intents but performs no authorization or execution. */
    public RuntimePlan planWithoutTools(RuntimePlanningRequest request) {
        CandidateGeneration generated = generateCandidate(request, false);
        RuntimePlan candidatePlan = planInternal(request, false, generated).plan();
        java.util.List<CandidateGeneration> existing = pendingCandidates.computeIfAbsent(request.turnId(), ignored -> new java.util.concurrent.CopyOnWriteArrayList<>());
        UUID candidateId = UUID.nameUUIDFromBytes((request.turnId() + ":candidate:" + existing.size()).getBytes(StandardCharsets.UTF_8));
        CandidateGeneration validated = new CandidateGeneration(candidateId, generated.context(), generated.hiddenData(), generated.capability(),
                new GmPlanResult(candidatePlan, generated.result().provider(), generated.result().model(), generated.result().reasoning(),
                        generated.result().stateDelta(), generated.result().toolCalls()));
        existing.add(validated);
        return candidatePlan;
    }

    /** Materialize exactly the candidate selected by BestOfN, without another provider call. */
    public RuntimePlan executeSelected(RuntimePlanningRequest request, RuntimePlan selected) {
        return executeSelected(request, selected, -1);
    }

    public RuntimePlan executeSelected(RuntimePlanningRequest request, RuntimePlan selected, int candidateIndex) {
        return executeSelectedWithOutcomes(request, selected, candidateIndex).plan();
    }

    public RuntimePlanningResult executeSelectedWithOutcomes(RuntimePlanningRequest request, RuntimePlan selected, int candidateIndex) {
        java.util.List<CandidateGeneration> candidates = pendingCandidates.remove(request.turnId());
        if (candidates != null) {
            if (candidateIndex >= 0 && candidateIndex < candidates.size()) {
                return planInternal(request, true, candidates.get(candidateIndex));
            }
            for (CandidateGeneration candidate : candidates) {
                if (candidate.result().plan().equals(selected)) return planInternal(request, true, candidate);
            }
        }
        throw new IllegalStateException("selected candidate is no longer available for materialization");
    }

    private RuntimePlanningResult planInternal(RuntimePlanningRequest request, boolean executeTools) {
        return planInternal(request, executeTools, null);
    }

    private RuntimePlanningResult planInternal(RuntimePlanningRequest request, boolean executeTools, CandidateGeneration supplied) {
        CandidateGeneration generated = supplied == null ? generateCandidate(request) : supplied;
        GmContextEnvelope context = generated.context();
        java.util.Set<String> hiddenData = generated.hiddenData();
        // Candidate generation is intentionally capability-free. A selected
        // candidate receives a fresh capability only at materialization time.
        TurnCapability capability = generated.capability();
        if (executeTools && capability == null && !generated.result().toolCalls().isEmpty()) {
            capability = issueCapability(request);
        }
        GmPlanResult result = validateCandidate(request, generated.result(), hiddenData);
        if (executeTools && !result.toolCalls().isEmpty()) {
            if (gateway == null || saga == null) throw new IllegalStateException("GM tool gateway is not configured");
            final TurnCapability executionCapability = capability;
            GmToolGateway saggedGateway = createSagaGateway();
            List<GmToolExecutionLoop.PlannedToolCall> calls = prepareToolBatch(request, generated.candidateId(), result);
            // Reject the whole batch before dispatch so a later unauthorized call
            // cannot leave an earlier tool mutation partially applied.
            try {
                calls.forEach(call -> {
                    LOGGER.info("gm_tool_preflight_started turnId={} toolName={}", request.turnId(), call.invocation().toolName());
                    if (gateway != null) gateway.preflight(executionCapability, call.invocation());
                    else executionCapability.authorize(call.invocation(), java.time.Instant.now());
                    LOGGER.info("gm_tool_preflight_passed turnId={} toolName={}", request.turnId(), call.invocation().toolName());
                });
            } catch (RuntimeException failure) {
                LOGGER.error("gm_tool_preflight_failed turnId={} exceptionClass={} exceptionMessage={}", request.turnId(),
                        failure.getClass().getName(), failure.getMessage(), failure);
                throw failure;
            }
            ToolMaterialization materialization = executeToolSaga(request, context, executionCapability, calls, saggedGateway, result);
            result = materialization.result();
            return new RuntimePlanningResult(finalizeCandidate(request, result, hiddenData, materialization.outcomes()), materialization.outcomes());
        } else if (capability != null) {
            gateway.revoke(capability);
        }
        return new RuntimePlanningResult(finalizeCandidate(request, result, hiddenData, List.of()), List.of());
    }

    /** Gate 0 seam: provider candidate generation and semantic validation are isolated from execution. */
    private CandidateGeneration generateCandidate(RuntimePlanningRequest request) {
        return generateCandidate(request, true);
    }

    private CandidateGeneration generateCandidate(RuntimePlanningRequest request, boolean issueCapability) {
        GmContextEnvelope context = new GmContextEnvelope(request.adventureId(), request.ownerPlayerId(), request.sessionId(), request.turnId(), request.scenarioPackageId(),
                request.bindingVersion(), request.currentContext(), request.activeSourceContext(), request.action(), request.evidencePack(),
                request.recentTurns(), request.characterSnapshots(), request.storyPlanContext(), request.provider(), request.model(), request.reasoning(),
                requestedSelection(request), request.narrativeContext());
        java.util.Set<String> hiddenData = context.storyPlanContext().isBlank() ? java.util.Set.of() : java.util.Set.of(context.storyPlanContext());
        TurnCapability capability = issueCapability ? issueCapability(request) : null;
        List<GmToolSpec> modelTools = gateway == null ? List.of() : gateway.modelTools().stream()
                .filter(spec -> capability == null || capability.allowedTools().contains(spec.name())).toList();
        GmPlanResult result = capability == null ? agentPort.plan(context, modelTools) : agentPort.plan(context, capability, modelTools);
        LOGGER.info("gm_tool_plan_received turnId={} toolCount={} tools={}", request.turnId(), result.toolCalls().size(),
                result.toolCalls().stream().map(call -> call.toolName()).toList());
        return new CandidateGeneration(UUID.nameUUIDFromBytes((request.turnId() + ":candidate:direct").getBytes(StandardCharsets.UTF_8)), context, hiddenData, capability, result);
    }

    private TurnCapability issueCapability(RuntimePlanningRequest request) {
        if (gateway == null || saga == null) return null;
        return TurnCapability.issue(request.sessionId(), request.turnId(), request.ownerPlayerId().value(),
                Set.of("dice.roll", "character.update", "revise_story_plan", "advance_game_time"),
                java.time.Instant.now().plusSeconds(300), UUID.randomUUID());
    }

    private GmPlanResult validateCandidate(RuntimePlanningRequest request, GmPlanResult candidate, java.util.Set<String> hiddenData) {
        return validator.validate(candidate, request.evidencePack(), request.currentContext(), hiddenData);
    }

    /** Gate 0-E/F: final semantic validation and RuntimePlan/state-delta assembly. */
    private RuntimePlan finalizeCandidate(RuntimePlanningRequest request, GmPlanResult result, java.util.Set<String> hiddenData,
                                          List<RuntimeCommandOutcome> outcomes) {
        GmPlanResult validated = validator.validate(result, request.evidencePack(), request.currentContext(), hiddenData);
        RuntimePlan plan;
        if (validated.stateDelta().isEmpty()) {
            plan = validated.plan();
        } else {
            long expectedVersion = request.narrativeContext() == null ? 0 : request.narrativeContext().stateVersion();
            plan = validated.plan().withStateDelta(translateStateDelta(validated.stateDelta(), expectedVersion));
        }
        return finalizationPort.finalize(plan, outcomes);
    }

    /** Gate 0-C: convert validated tool intents into server-owned invocations without executing them. */
    private List<GmToolExecutionLoop.PlannedToolCall> prepareToolBatch(RuntimePlanningRequest request, UUID candidateId, GmPlanResult result) {
        List<GmToolExecutionLoop.PlannedToolCall> calls = new java.util.ArrayList<>();
        for (int index = 0; index < result.toolCalls().size(); index++) {
            GmToolCall call = result.toolCalls().get(index);
            UUID id = UUID.nameUUIDFromBytes((request.turnId() + ":" + candidateId + ":" + index).getBytes(StandardCharsets.UTF_8));
            calls.add(new GmToolExecutionLoop.PlannedToolCall(new GmToolInvocation(id, request.sessionId(), request.turnId(), request.ownerPlayerId().value(), call.toolName(), call.argumentsJson(),
                    request.ruleSetId() == null ? null : new GmToolExecutionContext(request.adventureId().value(), request.ruleSetId(), request.narrativeContext() == null ? 0 : request.narrativeContext().stateVersion()), candidateId, index), call.required()));
        }
        return List.copyOf(calls);
    }

    /** Gate 0-C: isolate saga/idempotency transport from candidate materialization. */
    private GmToolGateway createSagaGateway() {
        return (cap, invocation) -> {
            RuntimeCommandRequest command = new RuntimeCommandRequest(invocation.invocationId(), invocation.sessionId(), invocation.turnId(), invocation.ownerPlayerId(), invocation.toolName(), invocation.argumentsJson(), invocation.candidateId(), invocation.toolIndex());
            RuntimeCommandOutcome outcome = saga.execute(command, ignored -> {
                GmToolOutcome tool = gateway.invoke(cap, invocation);
                return tool.status() == GmToolOutcome.Status.COMPLETED
                        ? RuntimeCommandOutcome.applied(tool.value(), tool.version(), tool.reference())
                        : RuntimeCommandOutcome.rejected(tool.value());
            }, commandId -> gateway.query(invocation.toolName(), commandId).map(tool ->
                    tool.status() == GmToolOutcome.Status.COMPLETED
                            ? RuntimeCommandOutcome.applied(tool.value(), tool.version(), tool.reference())
                            : RuntimeCommandOutcome.rejected(tool.value())));
            return switch (outcome.status()) {
                case APPLIED -> GmToolOutcome.completed(outcome.value());
                case UNKNOWN -> GmToolOutcome.unknown(outcome.value());
                default -> GmToolOutcome.rejected(outcome.value());
            };
        };
    }

    /** Gate 0-C: execute the prepared batch and fold outcomes into the current plan. */
    private ToolMaterialization executeToolSaga(RuntimePlanningRequest request, GmContextEnvelope context,
                                         TurnCapability capability, List<GmToolExecutionLoop.PlannedToolCall> calls,
                                         GmToolGateway saggedGateway, GmPlanResult result) {
        try {
            GmToolExecutionLoop.Result execution = new GmToolExecutionLoop(saggedGateway, 8).act(capability, calls,
                    failed -> {
                        GmToolCall repaired = agentPort.repair(context,
                                new GmToolCall(failed.invocation().toolName(), failed.invocation().argumentsJson(), failed.required()));
                        if (repaired == null) return null;
                            return new GmToolExecutionLoop.PlannedToolCall(new GmToolInvocation(
                                    failed.invocation().invocationId(), request.sessionId(), request.turnId(), request.ownerPlayerId().value(),
                                    repaired.toolName(), repaired.argumentsJson(), failed.invocation().executionContext(),
                                    failed.invocation().candidateId(), failed.invocation().toolIndex()), repaired.required());
                    });
            for (int index = 0; index < execution.outcomes().size(); index++) {
                if (execution.outcomes().get(index).status() != GmToolOutcome.Status.UNKNOWN) continue;
                UUID commandId = calls.get(index).invocation().invocationId();
                String toolName = calls.get(index).invocation().toolName();
                saga.resume(commandId, ignored -> { throw new IllegalStateException("unknown tool outcome"); },
                        ignored -> gateway.query(toolName, commandId).map(outcome -> outcome.status() == GmToolOutcome.Status.COMPLETED
                                ? RuntimeCommandOutcome.applied(outcome.value(), 0)
                                : RuntimeCommandOutcome.rejected(outcome.value())).orElseThrow());
            }
            if (execution.outcomes().stream().anyMatch(outcome -> outcome.status() == GmToolOutcome.Status.REJECTED
                    || outcome.status() == GmToolOutcome.Status.REQUIRES_CHOICE)) {
                RuntimePlan safe = new RuntimePlan(result.plan().scene(), result.plan().npcState(), result.plan().judgment(),
                        "The requested action needs clarification before it can be completed.", result.plan().proposedActiveSourceContext(),
                        result.plan().citedEvidence(), result.plan().warnings(), result.plan().provider(), result.plan().model(), result.plan().reasoning(),
                        result.plan().advanceStoryPlan(), result.plan().selectedBranchId(), result.plan().requestedSelection(), result.plan().effectiveSelection(),
                        result.plan().attemptCount(), result.plan().citationBindings(), result.plan().stateDelta());
                return new ToolMaterialization(new GmPlanResult(safe, result.provider(), result.model(), result.reasoning(), result.stateDelta(), result.toolCalls()),
                        execution.outcomes().stream().map(GmAgentRuntimePlanningAdapter::toCommandOutcome).toList());
            }
            return new ToolMaterialization(result, execution.outcomes().stream().map(GmAgentRuntimePlanningAdapter::toCommandOutcome).toList());
        } finally {
            gateway.revoke(capability);
        }
    }

    private record ToolMaterialization(GmPlanResult result, List<RuntimeCommandOutcome> outcomes) { }

    private static RuntimeCommandOutcome toCommandOutcome(GmToolOutcome outcome) {
        return switch (outcome.status()) {
            case COMPLETED -> RuntimeCommandOutcome.applied(outcome.value(), outcome.version(), outcome.reference());
            case UNKNOWN -> RuntimeCommandOutcome.unknown(outcome.value());
            case REJECTED, REQUIRES_CHOICE -> RuntimeCommandOutcome.rejected(outcome.value());
        };
    }

    private record CandidateGeneration(UUID candidateId, GmContextEnvelope context, java.util.Set<String> hiddenData,
                                       TurnCapability capability, GmPlanResult result) { }

    /**
     * Provider state deltas are deliberately a small, explicit command language.
     * A bare id, citation key, or prose description is not a state mutation.
     */
    private static com.dndmaster.adventure.domain.runtime.narrative.StateDelta translateStateDelta(
            List<String> rawDeltas, long expectedVersion) {
        java.util.Set<String> changed = new java.util.LinkedHashSet<>();
        java.util.Set<String> revealed = new java.util.LinkedHashSet<>();
        for (String raw : rawDeltas) {
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException("unsupported state delta: blank item");
            }
            String[] parts = raw.trim().split(":", 2);
            if (parts.length != 2 || parts[1].isBlank()) {
                throw new IllegalArgumentException("unsupported state delta: " + raw);
            }
            String factId = parts[1].trim();
            switch (parts[0].trim().toLowerCase(java.util.Locale.ROOT)) {
                case "change" -> changed.add(factId);
                case "reveal" -> revealed.add(factId);
                default -> throw new IllegalArgumentException("unsupported state delta: " + raw);
            }
        }
        return new com.dndmaster.adventure.domain.runtime.narrative.StateDelta(
                expectedVersion, changed, revealed, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static RequestedGmProviderSelection requestedSelection(RuntimePlanningRequest request) {
        if (request.provider().isBlank() || request.model().isBlank() || request.reasoning().isBlank()) {
            return RequestedGmProviderSelection.legacyUnknown();
        }
        return new RequestedGmProviderSelection(request.providerEndpointId(), request.provider(), request.model(), request.reasoning());
    }

}
