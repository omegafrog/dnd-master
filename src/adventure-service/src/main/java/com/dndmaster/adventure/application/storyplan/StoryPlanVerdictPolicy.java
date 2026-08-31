package com.dndmaster.adventure.application.storyplan;

import com.dndmaster.adventure.domain.adventure.SemanticVerdict;
import com.dndmaster.adventure.domain.adventure.SemanticVerdictType;

public final class StoryPlanVerdictPolicy {
    private StoryPlanVerdictPolicy() {}
    public enum Decision { ACCEPT, READY_WITH_WARNING, RETRY, BLOCK }
    public static Decision decide(SemanticVerdict verdict, int attempt, int maxAttempts) {
        if (verdict == null || verdict.failureCode().equals("JUDGE_UNAVAILABLE")) return attempt < maxAttempts ? Decision.RETRY : Decision.BLOCK;
        return switch (verdict.type()) {
            case COMPATIBLE -> Decision.ACCEPT;
            case UNCERTAIN -> Decision.READY_WITH_WARNING;
            // Semantic review is advisory: deterministic projection/source checks
            // remain the execution gate, while a model contradiction is retained
            // as a warning so a usable plan is not discarded on verifier variance.
            case CONTRADICTORY -> Decision.READY_WITH_WARNING;
        };
    }
}
