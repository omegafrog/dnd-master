package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.ActiveSourceContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import java.util.Objects;
import java.util.UUID;

// 계획 단계에 넘기는 입력값이다. 현재 문맥과 근거를 같이 전달한다.
public record RuntimePlanningRequest(
        AdventureId adventureId,
        OwnerPlayerId ownerPlayerId,
        UUID scenarioPackageId,
        long bindingVersion,
        AdventureContext currentContext,
        ActiveSourceContext activeSourceContext,
        String action,
        EvidencePack evidencePack) {
    public RuntimePlanningRequest {
        adventureId = Objects.requireNonNull(adventureId, "adventure id must not be null");
        ownerPlayerId = Objects.requireNonNull(ownerPlayerId, "owner player id must not be null");
        scenarioPackageId = Objects.requireNonNull(scenarioPackageId, "scenario package id must not be null");
        currentContext = Objects.requireNonNull(currentContext, "current context must not be null");
        action = required(action, "action");
        evidencePack = Objects.requireNonNull(evidencePack, "evidence pack must not be null");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
