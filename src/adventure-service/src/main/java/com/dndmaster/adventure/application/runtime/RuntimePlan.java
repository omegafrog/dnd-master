package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.ActiveSourceContext;
import java.util.List;
import java.util.Objects;

// 계획 단계가 만든 턴 결과 초안이다. 장면, 판정, narration, 다음 근거 위치를 담는다.
public record RuntimePlan(
        String scene,
        String npcState,
        String judgment,
        String narration,
        ActiveSourceContext proposedActiveSourceContext,
        List<RuntimeEvidence> citedEvidence,
        List<String> warnings) {
    public RuntimePlan {
        scene = required(scene, "scene");
        judgment = required(judgment, "judgment");
        narration = required(narration, "narration");
        citedEvidence = List.copyOf(Objects.requireNonNull(citedEvidence, "cited evidence must not be null"));
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings must not be null"));
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
