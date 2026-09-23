package com.dndmaster.adventure.application.runtime;

import java.util.Objects;

/**
 * Separates the provider's scene label from the canonical narrative transition.
 *
 * <p>A map entry or map movement may change what is shown on the map without
 * changing the story situation. Only a grounded transition proposal may replace
 * the persisted narrative scene.</p>
 */
public final class NarrativeSceneGroundingPolicy {
    private NarrativeSceneGroundingPolicy() {}

    public static RuntimePlan apply(RuntimePlan plan, String currentScene, RuntimeResolutionProposal proposal) {
        Objects.requireNonNull(plan, "plan must not be null");
        if (currentScene == null || currentScene.isBlank()) {
            throw new IllegalArgumentException("current scene must not be blank");
        }
        if (proposal != null && proposal.situationProposal() != null
                && proposal.situationUpdate() != null
                && proposal.situationUpdate().kind() == SituationUpdateProposal.Kind.TRANSITION) {
            return plan;
        }
        return plan.withScene(currentScene);
    }
}
