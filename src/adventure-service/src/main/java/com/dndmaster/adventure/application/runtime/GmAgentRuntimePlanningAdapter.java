package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.Objects;

public final class GmAgentRuntimePlanningAdapter implements RuntimePlanningPort {
    private final GmAgentPort agentPort;
    private final GmFinalValidator validator;

    public GmAgentRuntimePlanningAdapter(GmAgentPort agentPort, GmFinalValidator validator) {
        this.agentPort = Objects.requireNonNull(agentPort);
        this.validator = Objects.requireNonNull(validator);
    }

    @Override
    public RuntimePlan plan(RuntimePlanningRequest request) {
        GmContextEnvelope context = new GmContextEnvelope(request.adventureId(), request.ownerPlayerId(), request.scenarioPackageId(),
                request.bindingVersion(), request.currentContext(), request.activeSourceContext(), request.action(), request.evidencePack(), List.of());
        GmPlanResult result = validator.validate(agentPort.plan(context), request.evidencePack(), request.currentContext(), java.util.Set.of());
        return result.plan();
    }
}
