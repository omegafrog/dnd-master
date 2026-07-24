package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.ActiveSourceContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

// 저장된 런타임 턴이다. 어떤 근거로 어떤 응답을 냈는지 함께 남긴다.
public record RuntimeTurn(
        UUID turnId,
        AdventureId adventureId,
        UUID scenarioPackageId,
        long bindingVersion,
        String action,
        EvidencePack evidencePack,
        RuntimePlan plan,
        ActiveSourceContext activeSourceContext,
        List<String> citations,
        List<String> warnings) {
    public RuntimeTurn {
        turnId = Objects.requireNonNull(turnId, "turn id must not be null");
        adventureId = Objects.requireNonNull(adventureId, "adventure id must not be null");
        scenarioPackageId = Objects.requireNonNull(scenarioPackageId, "scenario package id must not be null");
        action = required(action, "action");
        evidencePack = Objects.requireNonNull(evidencePack, "evidence pack must not be null");
        plan = Objects.requireNonNull(plan, "plan must not be null");
        citations = List.copyOf(Objects.requireNonNull(citations, "citations must not be null"));
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings must not be null"));
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
