package com.dndmaster.adventure.domain.runtime.story;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** AI semantic proposal; it has no persistence authority of its own. */
public record StoryRuntimeProposal(UUID proposalId, long expectedVersion, UUID scenarioPackageId,
        long backboneRevision, String stageId, long detailedStageRevision, SituationAction situationAction,
        List<String> learnedRevelationIds, List<PressureProposal> pressureProposals,
        List<String> satisfiedPredicateIds, UnresolvedStageExit unresolvedExit) {
    public StoryRuntimeProposal {
        proposalId = Objects.requireNonNull(proposalId, "proposal id must not be null");
        if (expectedVersion < 0) throw new IllegalArgumentException("expected runtime version must not be negative");
        scenarioPackageId = Objects.requireNonNull(scenarioPackageId, "scenario package id must not be null");
        if (backboneRevision < 1 || detailedStageRevision < 1) throw new IllegalArgumentException("stage revisions must be positive");
        if (stageId == null || stageId.isBlank()) throw new IllegalArgumentException("stage id is required");
        situationAction = Objects.requireNonNull(situationAction, "situation action must not be null");
        learnedRevelationIds = List.copyOf(learnedRevelationIds == null ? List.of() : learnedRevelationIds);
        pressureProposals = List.copyOf(pressureProposals == null ? List.of() : pressureProposals);
        satisfiedPredicateIds = List.copyOf(satisfiedPredicateIds == null ? List.of() : satisfiedPredicateIds);
        unresolvedExit = unresolvedExit;
        if (learnedRevelationIds.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new IllegalArgumentException("learned revelation ids must not be blank");
        }
        if (satisfiedPredicateIds.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new IllegalArgumentException("satisfied predicate ids must not be blank");
        }
        stageId = stageId.trim();
    }

    /** Source-compatible constructor for proposals created before Funnel transition support. */
    public StoryRuntimeProposal(UUID proposalId, long expectedVersion, UUID scenarioPackageId,
            long backboneRevision, String stageId, long detailedStageRevision, SituationAction situationAction,
            List<String> learnedRevelationIds, List<PressureProposal> pressureProposals) {
        this(proposalId, expectedVersion, scenarioPackageId, backboneRevision, stageId, detailedStageRevision,
                situationAction, learnedRevelationIds, pressureProposals, List.of(), null);
    }

    public StoryRuntimeProposal withExpectedVersion(long version) {
        return new StoryRuntimeProposal(proposalId, version, scenarioPackageId, backboneRevision, stageId,
                detailedStageRevision, situationAction, learnedRevelationIds, pressureProposals,
                satisfiedPredicateIds, unresolvedExit);
    }

    public StoryRuntimeProposal withSatisfiedPredicateIds(List<String> predicateIds) {
        return new StoryRuntimeProposal(proposalId, expectedVersion, scenarioPackageId, backboneRevision, stageId,
                detailedStageRevision, situationAction, learnedRevelationIds, pressureProposals, predicateIds, unresolvedExit);
    }

    public StoryRuntimeProposal withUnresolvedExit(UnresolvedStageExit exit) {
        return new StoryRuntimeProposal(proposalId, expectedVersion, scenarioPackageId, backboneRevision, stageId,
                detailedStageRevision, situationAction, learnedRevelationIds, pressureProposals,
                satisfiedPredicateIds, exit);
    }
}
