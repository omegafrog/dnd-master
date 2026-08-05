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
        UUID scenarioPackageId,
        long bindingVersion,
        AdventureContext currentContext,
        ActiveSourceContext activeSourceContext,
        String action,
        EvidencePack evidencePack,
        List<String> recentTurns) {
    public GmContextEnvelope {
        adventureId = Objects.requireNonNull(adventureId);
        ownerPlayerId = Objects.requireNonNull(ownerPlayerId);
        scenarioPackageId = Objects.requireNonNull(scenarioPackageId);
        currentContext = Objects.requireNonNull(currentContext);
        action = required(action);
        evidencePack = Objects.requireNonNull(evidencePack);
        recentTurns = List.copyOf(Objects.requireNonNull(recentTurns));
        if (bindingVersion < 0) throw new IllegalArgumentException("binding version must not be negative");
    }

    public String operationKey() {
        return adventureId.value() + ":" + scenarioPackageId + ":" + bindingVersion + ":" + action + ":" + UUID.randomUUID();
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("action must not be blank");
        return value.trim();
    }
}
