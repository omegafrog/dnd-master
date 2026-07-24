package com.dndmaster.adventure.application.runtime;

// narration 안전 검사 결과다. 실패면 여기서 턴을 막는다.
public record NarrationSafetyAssessment(boolean approved, String reason) {
    public NarrationSafetyAssessment {
        if (reason == null || reason.isBlank()) {
            reason = approved ? "approved" : "rejected";
        } else {
            reason = reason.trim();
        }
    }
}
