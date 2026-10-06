package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.Objects;

/** AI's proposed resolution of the compiled adventure objectives. */
public record CompletionCandidate(boolean complete, List<String> resolvedObjectiveIds,
        List<String> satisfiedResolutionCriteriaIds, String concludingScene) {
    public CompletionCandidate {
        resolvedObjectiveIds = List.copyOf(Objects.requireNonNull(resolvedObjectiveIds,
                "resolved objective ids are required"));
        satisfiedResolutionCriteriaIds = List.copyOf(Objects.requireNonNull(satisfiedResolutionCriteriaIds,
                "satisfied resolution criteria ids are required"));
        if (resolvedObjectiveIds.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new IllegalArgumentException("resolved objective ids must not be blank");
        }
        if (satisfiedResolutionCriteriaIds.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new IllegalArgumentException("satisfied resolution criteria ids must not be blank");
        }
        if (resolvedObjectiveIds.stream().distinct().count() != resolvedObjectiveIds.size()
                || satisfiedResolutionCriteriaIds.stream().distinct().count() != satisfiedResolutionCriteriaIds.size()) {
            throw new IllegalArgumentException("completion element ids must be unique");
        }
        concludingScene = concludingScene == null ? "" : concludingScene.trim();
        if (complete && concludingScene.isBlank()) {
            throw new IllegalArgumentException("a completed adventure requires a concluding scene");
        }
        if (!complete && !concludingScene.isBlank()) {
            throw new IllegalArgumentException("an incomplete adventure cannot propose a concluding scene");
        }
    }

    public static CompletionCandidate continueAdventure() {
        return new CompletionCandidate(false, List.of(), List.of(), "");
    }
}
