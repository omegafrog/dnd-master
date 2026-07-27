package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.AdventureContext;
import java.util.Objects;

// 출력 narration이 플레이어에게 나가도 되는지 검사할 입력값이다.
public record NarrationSafetyRequest(String narration, EvidencePack evidencePack, AdventureContext currentContext, String action) {
    public NarrationSafetyRequest {
        narration = required(narration, "narration");
        evidencePack = Objects.requireNonNull(evidencePack, "evidence pack must not be null");
        currentContext = Objects.requireNonNull(currentContext, "current context must not be null");
        action = required(action, "action");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
