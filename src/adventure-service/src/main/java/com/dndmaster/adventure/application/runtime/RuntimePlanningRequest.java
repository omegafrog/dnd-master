package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.ActiveSourceContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import java.util.Objects;
import java.util.UUID;
import com.dndmaster.adventure.domain.runtime.narrative.NarrativeContext;

// 계획 단계에 넘기는 입력값이다. 현재 문맥과 근거를 같이 전달한다.
public record RuntimePlanningRequest(
        AdventureId adventureId,
        OwnerPlayerId ownerPlayerId,
        UUID sessionId,
        UUID turnId,
        UUID scenarioPackageId,
        long bindingVersion,
        AdventureContext currentContext,
        ActiveSourceContext activeSourceContext,
        String action,
        EvidencePack evidencePack,
        java.util.List<String> recentTurns,
        java.util.List<String> characterSnapshots,
        String scenarioContext,
        UUID providerEndpointId,
        String provider,
        String model,
        String reasoning, NarrativeContext narrativeContext, UUID ruleSetId) {
    public RuntimePlanningRequest {
        adventureId = Objects.requireNonNull(adventureId, "adventure id must not be null");
        ownerPlayerId = Objects.requireNonNull(ownerPlayerId, "owner player id must not be null");
        sessionId = Objects.requireNonNull(sessionId, "session id must not be null");
        turnId = Objects.requireNonNull(turnId, "turn id must not be null");
        scenarioPackageId = Objects.requireNonNull(scenarioPackageId, "scenario package id must not be null");
        currentContext = Objects.requireNonNull(currentContext, "current context must not be null");
        action = required(action, "action");
        evidencePack = Objects.requireNonNull(evidencePack, "evidence pack must not be null");
        recentTurns = java.util.List.copyOf(Objects.requireNonNull(recentTurns));
        characterSnapshots = java.util.List.copyOf(Objects.requireNonNull(characterSnapshots));
        scenarioContext = scenarioContext == null ? "" : scenarioContext.trim();
        provider = provider == null ? "" : provider.trim();
        model = model == null ? "" : model.trim();
        reasoning = reasoning == null ? "" : reasoning.trim();
    }

    public RuntimePlanningRequest(AdventureId adventureId, OwnerPlayerId ownerPlayerId, UUID sessionId, UUID turnId,
            UUID scenarioPackageId, long bindingVersion, AdventureContext currentContext, ActiveSourceContext activeSourceContext,
            String action, EvidencePack evidencePack, java.util.List<String> recentTurns, java.util.List<String> characterSnapshots,
            String scenarioContext, UUID providerEndpointId, String provider, String model, String reasoning, NarrativeContext narrativeContext) {
        this(adventureId, ownerPlayerId, sessionId, turnId, scenarioPackageId, bindingVersion, currentContext, activeSourceContext,
                action, evidencePack, recentTurns, characterSnapshots, scenarioContext, providerEndpointId, provider, model,
                reasoning, narrativeContext, null);
    }

    public RuntimePlanningRequest(AdventureId adventureId, OwnerPlayerId ownerPlayerId, UUID scenarioPackageId,
                                  long bindingVersion, AdventureContext currentContext, ActiveSourceContext activeSourceContext,
                                  String action, EvidencePack evidencePack) {
        this(adventureId, ownerPlayerId, UUID.randomUUID(), UUID.randomUUID(), scenarioPackageId, bindingVersion, currentContext, activeSourceContext, action,
                evidencePack, java.util.List.of(), java.util.List.of(), "", null, "", "", "", null, null);
    }

    public RuntimePlanningRequest(AdventureId adventureId, OwnerPlayerId ownerPlayerId, UUID scenarioPackageId,
                                  long bindingVersion, AdventureContext currentContext, ActiveSourceContext activeSourceContext,
                                  String action, EvidencePack evidencePack, java.util.List<String> recentTurns,
                                  java.util.List<String> characterSnapshots, String scenarioContext) {
        this(adventureId, ownerPlayerId, UUID.randomUUID(), UUID.randomUUID(), scenarioPackageId, bindingVersion,
                currentContext, activeSourceContext, action, evidencePack, recentTurns, characterSnapshots, scenarioContext,
                null, "", "", "", null, null);
    }

    public RuntimePlanningRequest(AdventureId adventureId, OwnerPlayerId ownerPlayerId, UUID sessionId, UUID turnId,
                                  UUID scenarioPackageId, long bindingVersion, AdventureContext currentContext,
                                  ActiveSourceContext activeSourceContext, String action, EvidencePack evidencePack,
                                  java.util.List<String> recentTurns, java.util.List<String> characterSnapshots,
                                  String scenarioContext) {
        this(adventureId, ownerPlayerId, sessionId, turnId, scenarioPackageId, bindingVersion, currentContext,
                activeSourceContext, action, evidencePack, recentTurns, characterSnapshots, scenarioContext,
                null, "", "", "", null, null);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
