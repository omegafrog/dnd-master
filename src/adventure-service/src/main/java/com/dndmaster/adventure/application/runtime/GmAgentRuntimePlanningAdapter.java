package com.dndmaster.adventure.application.runtime;

import java.util.Objects;
import java.util.List;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import com.dndmaster.adventure.domain.runtime.RequestedGmProviderSelection;

public final class GmAgentRuntimePlanningAdapter implements RuntimePlanningPort {
    private final GmAgentPort agentPort;
    private final GmFinalValidator validator;
    private final GmToolGateway gateway;
    private final RuntimeCommandSagaApplicationService saga;

    public GmAgentRuntimePlanningAdapter(GmAgentPort agentPort, GmFinalValidator validator) {
        this(agentPort, validator, null, null);
    }

    public GmAgentRuntimePlanningAdapter(GmAgentPort agentPort, GmFinalValidator validator,
                                         GmToolGateway gateway, RuntimeCommandSagaApplicationService saga) {
        this.agentPort = Objects.requireNonNull(agentPort);
        this.validator = Objects.requireNonNull(validator);
        this.gateway = gateway;
        this.saga = saga;
    }

    @Override
    public RuntimePlan plan(RuntimePlanningRequest request) {
        GmContextEnvelope context = new GmContextEnvelope(request.adventureId(), request.ownerPlayerId(), request.sessionId(), request.turnId(), request.scenarioPackageId(),
                request.bindingVersion(), request.currentContext(), request.activeSourceContext(), request.action(), request.evidencePack(),
                request.recentTurns(), request.characterSnapshots(), request.storyPlanContext(), request.provider(), request.model(), request.reasoning(),
                requestedSelection(request), request.narrativeContext());
        java.util.Set<String> hiddenData = context.storyPlanContext().isBlank()
                ? java.util.Set.of()
                : java.util.Set.of(context.storyPlanContext());
        TurnCapability capability = gateway == null || saga == null ? null : TurnCapability.issue(
                request.sessionId(), request.turnId(), request.ownerPlayerId().value(), Set.of("dice.roll", "character.update", "revise_story_plan", "advance_game_time"),
                java.time.Instant.now().plusSeconds(60), UUID.nameUUIDFromBytes((request.sessionId() + ":" + request.turnId()).getBytes(StandardCharsets.UTF_8)));
        GmPlanResult result = capability == null ? agentPort.plan(context) : agentPort.plan(context, capability);
        result = validator.validate(result, request.evidencePack(), request.currentContext(), hiddenData);
        if (!result.toolCalls().isEmpty()) {
            if (gateway == null || saga == null) throw new IllegalStateException("GM tool gateway is not configured");
            GmToolGateway saggedGateway = (cap, invocation) -> {
                RuntimeCommandRequest command = new RuntimeCommandRequest(invocation.invocationId(), invocation.sessionId(), invocation.turnId(), invocation.ownerPlayerId(), invocation.toolName(), invocation.argumentsJson());
                RuntimeCommandOutcome outcome = saga.execute(command, ignored -> {
                    GmToolOutcome tool = gateway.invoke(cap, invocation);
                    return tool.status() == GmToolOutcome.Status.COMPLETED
                            ? RuntimeCommandOutcome.applied(tool.value(), tool.version(), tool.reference())
                            : RuntimeCommandOutcome.rejected(tool.value());
                });
                return switch (outcome.status()) {
                    case APPLIED -> GmToolOutcome.completed(outcome.value());
                    case UNKNOWN -> GmToolOutcome.unknown(outcome.value());
                    default -> GmToolOutcome.rejected(outcome.value());
                };
            };
            List<GmToolExecutionLoop.PlannedToolCall> calls = new java.util.ArrayList<>();
            for (int index = 0; index < result.toolCalls().size(); index++) {
                GmToolCall call = result.toolCalls().get(index);
                UUID id = UUID.nameUUIDFromBytes((request.turnId() + ":" + index + ":" + call.toolName() + ":" + call.argumentsJson()).getBytes(StandardCharsets.UTF_8));
                calls.add(new GmToolExecutionLoop.PlannedToolCall(new GmToolInvocation(id, request.sessionId(), request.turnId(), request.ownerPlayerId().value(), call.toolName(), call.argumentsJson()), call.required()));
            }
            try {
                GmToolExecutionLoop.Result execution = new GmToolExecutionLoop(saggedGateway, 8).act(capability, calls,
                        failed -> {
                            GmToolCall repaired = agentPort.repair(context,
                                    new GmToolCall(failed.invocation().toolName(), failed.invocation().argumentsJson(), failed.required()));
                            if (repaired == null) return null;
                            return new GmToolExecutionLoop.PlannedToolCall(new GmToolInvocation(
                                    failed.invocation().invocationId(), request.sessionId(), request.turnId(), request.ownerPlayerId().value(),
                                    repaired.toolName(), repaired.argumentsJson()), repaired.required());
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
                    result = new GmPlanResult(safe, result.provider(), result.model(), result.reasoning(), result.stateDelta(), result.toolCalls());
                }
            } finally {
                gateway.revoke(capability);
            }
        } else if (capability != null) {
            gateway.revoke(capability);
        }
        GmPlanResult validated = validator.validate(result, request.evidencePack(), request.currentContext(), hiddenData);
        if (validated.stateDelta().isEmpty()) return validated.plan();
        long expectedVersion = request.narrativeContext() == null ? 0 : request.narrativeContext().stateVersion();
        return validated.plan().withStateDelta(new com.dndmaster.adventure.domain.runtime.narrative.StateDelta(
                expectedVersion, java.util.Set.copyOf(validated.stateDelta()), java.util.Set.copyOf(validated.stateDelta()),
                List.of(), List.of(), List.of(), List.of(), List.of()));
    }

    private static RequestedGmProviderSelection requestedSelection(RuntimePlanningRequest request) {
        if (request.provider().isBlank() || request.model().isBlank() || request.reasoning().isBlank()) {
            return RequestedGmProviderSelection.legacyUnknown();
        }
        return new RequestedGmProviderSelection(request.providerEndpointId(), request.provider(), request.model(), request.reasoning());
    }

}
