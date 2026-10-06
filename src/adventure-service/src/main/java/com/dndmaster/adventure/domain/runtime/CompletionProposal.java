package com.dndmaster.adventure.domain.runtime;

/** 게임 마스터가 제안한 모험 완료 상태. 모험 집계가 안전한 턴과 함께 반영한다. */
public record CompletionProposal(boolean complete, String concludingScene,
        java.util.List<String> resolvedObjectiveIds, java.util.List<String> satisfiedResolutionCriteriaIds) {
    public CompletionProposal {
        concludingScene = concludingScene == null ? "" : concludingScene.trim();
        resolvedObjectiveIds = resolvedObjectiveIds == null ? java.util.List.of() : java.util.List.copyOf(resolvedObjectiveIds);
        satisfiedResolutionCriteriaIds = satisfiedResolutionCriteriaIds == null
                ? java.util.List.of() : java.util.List.copyOf(satisfiedResolutionCriteriaIds);
        if (complete && concludingScene.isBlank()) {
            throw new IllegalArgumentException("completed adventure requires a concluding scene");
        }
        if (!complete && (!resolvedObjectiveIds.isEmpty() || !satisfiedResolutionCriteriaIds.isEmpty())) {
            throw new IllegalArgumentException("an incomplete adventure cannot resolve objectives or conditions");
        }
    }

    public CompletionProposal(boolean complete, String concludingScene) {
        this(complete, concludingScene, java.util.List.of(), java.util.List.of());
    }

    public static CompletionProposal continueAdventure() {
        return new CompletionProposal(false, "", java.util.List.of(), java.util.List.of());
    }
}
