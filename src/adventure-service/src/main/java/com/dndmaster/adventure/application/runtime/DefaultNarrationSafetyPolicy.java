package com.dndmaster.adventure.application.runtime;

/** Deterministic checks applied before narration is returned to the player. */
public final class DefaultNarrationSafetyPolicy implements NarrationSafetyPort {
    @Override
    public NarrationSafetyAssessment assess(NarrationSafetyRequest request) {
        String narration = request.narration();
        String reason = "approved";
        if (narration == null || narration.isBlank()) reason = "blank narration";
        else if (NarrationLeakDetector.isLikelySourceLeak(narration, request.evidencePack())) reason = "source leak or prohibited reference detected";
        else if (request.hiddenFacts().stream().anyMatch(narration::contains)) reason = "unrevealed scenario fact detected";
        return new NarrationSafetyAssessment("approved".equals(reason), reason);
    }
}
