package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.AdventureContext;
import java.util.Objects;

// 출력 narration이 플레이어에게 나가도 되는지 검사할 입력값이다.
public record NarrationSafetyRequest(String narration, EvidencePack evidencePack, AdventureContext currentContext, String action,
        java.util.List<String> hiddenFacts) {
    public NarrationSafetyRequest {
        narration = required(narration, "narration");
        evidencePack = Objects.requireNonNull(evidencePack, "evidence pack must not be null");
        currentContext = Objects.requireNonNull(currentContext, "current context must not be null");
        action = required(action, "action");
        hiddenFacts = java.util.List.copyOf(Objects.requireNonNull(hiddenFacts, "hidden facts must not be null"));
        if (hiddenFacts.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("hidden facts must not contain blank values");
        }
    }

    public NarrationSafetyRequest(String narration, EvidencePack evidencePack, AdventureContext currentContext, String action) {
        this(narration, evidencePack, currentContext, action, java.util.List.of());
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
