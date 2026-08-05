package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.ActiveSourceContext;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Complete provider input. It carries only the locked session knowledge set. */
public record GmContextEnvelope(
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
        List<String> recentTurns,
        List<String> characterSnapshots,
        String storyPlanContext) {
    public GmContextEnvelope {
        adventureId = Objects.requireNonNull(adventureId);
        ownerPlayerId = Objects.requireNonNull(ownerPlayerId);
        sessionId = Objects.requireNonNull(sessionId);
        turnId = Objects.requireNonNull(turnId);
        scenarioPackageId = Objects.requireNonNull(scenarioPackageId);
        currentContext = Objects.requireNonNull(currentContext);
        action = required(action);
        evidencePack = Objects.requireNonNull(evidencePack);
        recentTurns = List.copyOf(Objects.requireNonNull(recentTurns));
        characterSnapshots = List.copyOf(Objects.requireNonNull(characterSnapshots));
        storyPlanContext = storyPlanContext == null ? "" : storyPlanContext.trim();
        if (bindingVersion < 0) throw new IllegalArgumentException("binding version must not be negative");
    }

    public GmContextEnvelope(com.dndmaster.adventure.domain.adventure.AdventureId adventureId,
                             com.dndmaster.adventure.domain.adventure.OwnerPlayerId ownerPlayerId, UUID scenarioPackageId,
                             long bindingVersion, AdventureContext currentContext, ActiveSourceContext activeSourceContext,
                             String action, EvidencePack evidencePack, List<String> recentTurns) {
        this(adventureId, ownerPlayerId, UUID.randomUUID(), UUID.randomUUID(), scenarioPackageId, bindingVersion, currentContext, activeSourceContext, action,
                evidencePack, recentTurns, List.of(), "");
    }

    public String operationKey() {
        return adventureId.value() + ":" + scenarioPackageId + ":" + bindingVersion + ":" + turnId;
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("action must not be blank");
        return value.trim();
    }
}
