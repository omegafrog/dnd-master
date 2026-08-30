package com.dndmaster.adventure.application.storyplan;

import java.util.List;
import java.util.Optional;

/** Resolves and validates the typed, dependency-expanded repair boundary. */
public final class RepairScopeResolver {
    public RepairScope resolve(String fullSerializedCandidate,
            List<AdventureStoryPlanProjectionViolation> violations) {
        return tryResolve(fullSerializedCandidate, violations)
                .orElseThrow(() -> new IllegalArgumentException("repair scope could not be resolved"));
    }

    public Optional<RepairScope> tryResolve(String fullSerializedCandidate,
            List<AdventureStoryPlanProjectionViolation> violations) {
        if (fullSerializedCandidate == null || fullSerializedCandidate.isBlank()
                || violations == null || violations.isEmpty()
                || violations.stream().anyMatch(item -> item == null || !knownPath(item.fieldPath()))) {
            return Optional.empty();
        }
        try {
            return Optional.of(AdventureStoryPlanProjectionDependencyPolicy.scope(
                    fullSerializedCandidate, violations));
        } catch (RuntimeException unresolved) {
            return Optional.empty();
        }
    }

    private static boolean knownPath(String path) {
        String normalized = RepairScope.normalize(path);
        return normalized.matches("stages\\[(?:\\d+|\\*)\\](?:\\.(?:title|goal|conflict|transitionCondition|clearCondition|failureCondition|rules(?:\\.(?:check|outcome))?|evidence(?:\\[\\*\\])?\\.citationKey|combatSkeleton(?:\\.(?:participants(?:\\[(?:\\d+|\\*)\\])?(?:\\.(?:participantId|role|name|minimumCount|maximumCount))?|rewards(?:\\[(?:\\d+|\\*)\\])?|objective|startTrigger|successOutcome|failureOutcome))?|sourceFactClaims(?:\\[(?:\\d+|\\*)\\])?|tacticalPreparationRequirement|mapDefinitionId|mapAssetId|mapAssetLocator))?");
    }
}
