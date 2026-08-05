package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.Objects;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

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
                request.recentTurns(), request.characterSnapshots(), request.storyPlanContext());
        java.util.Set<String> hiddenData = context.storyPlanContext().isBlank()
                ? java.util.Set.of()
                : java.util.Set.of(context.storyPlanContext());
        GmPlanResult result = agentPort.plan(context);
        if (!result.toolCalls().isEmpty()) {
            if (gateway == null || saga == null) throw new IllegalStateException("GM tool gateway is not configured");
            TurnCapability capability = TurnCapability.issue(request.sessionId(), request.turnId(), request.ownerPlayerId().value(),
                    result.toolCalls().stream().map(GmToolCall::toolName).collect(java.util.stream.Collectors.toSet()),
                    java.time.Instant.now().plusSeconds(60), UUID.nameUUIDFromBytes((request.sessionId() + ":" + request.turnId()).getBytes(StandardCharsets.UTF_8)));
            GmToolGateway saggedGateway = (cap, invocation) -> {
                RuntimeCommandRequest command = new RuntimeCommandRequest(invocation.invocationId(), invocation.sessionId(), invocation.turnId(), invocation.ownerPlayerId(), invocation.toolName(), invocation.argumentsJson());
                RuntimeCommandOutcome outcome = saga.execute(command, ignored -> {
                    GmToolOutcome tool = gateway.invoke(cap, invocation);
                    return tool.status() == GmToolOutcome.Status.COMPLETED
                            ? RuntimeCommandOutcome.applied(tool.value(), 0)
                            : RuntimeCommandOutcome.rejected(tool.value());
                });
                return outcome.status() == RuntimeCommandStatus.APPLIED ? GmToolOutcome.completed(outcome.value()) : GmToolOutcome.rejected(outcome.value());
            };
            List<GmToolExecutionLoop.PlannedToolCall> calls = new java.util.ArrayList<>();
            for (int index = 0; index < result.toolCalls().size(); index++) {
                GmToolCall call = result.toolCalls().get(index);
                UUID id = UUID.nameUUIDFromBytes((request.turnId() + ":" + index + ":" + call.toolName() + ":" + call.argumentsJson()).getBytes(StandardCharsets.UTF_8));
                calls.add(new GmToolExecutionLoop.PlannedToolCall(new GmToolInvocation(id, request.sessionId(), request.turnId(), request.ownerPlayerId().value(), call.toolName(), call.argumentsJson()), call.required()));
            }
            try {
                new GmToolExecutionLoop(saggedGateway, 8).act(capability, calls, ignored -> null);
            } finally {
                gateway.revoke(capability);
            }
        }
        GmPlanResult validated = validator.validate(result, request.evidencePack(), request.currentContext(), hiddenData);
        return validated.plan();
    }
}
