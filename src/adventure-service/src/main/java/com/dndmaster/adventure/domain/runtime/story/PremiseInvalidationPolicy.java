package com.dndmaster.adventure.domain.runtime.story;

import com.dndmaster.adventure.domain.scenario.StageBackbone;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Deterministic gate for selective future replanning. */
public final class PremiseInvalidationPolicy {
    private PremiseInvalidationPolicy() {}

    public static PremiseInvalidationResult verify(StoryRuntimeState state, StageBackbone backbone,
            PremiseInvalidationProposal proposal) {
        Objects.requireNonNull(state, "story runtime state must not be null");
        Objects.requireNonNull(backbone, "backbone must not be null");
        Objects.requireNonNull(proposal, "invalidation proposal must not be null");
        PremiseInvalidationAudit.Reason reason = reason(state, backbone, proposal);
        boolean accepted = reason == PremiseInvalidationAudit.Reason.ACCEPTED;
        return new PremiseInvalidationResult(accepted, audit(proposal, accepted, reason));
    }

    public static PremiseInvalidationResult verifyOrThrow(StoryRuntimeState state, StageBackbone backbone,
            PremiseInvalidationProposal proposal) {
        PremiseInvalidationResult result = verify(state, backbone, proposal);
        if (!result.accepted()) throw new IllegalStateException("premise invalidation rejected (version/reference): " + result.audit().reason());
        return result;
    }

    private static PremiseInvalidationAudit.Reason reason(StoryRuntimeState state, StageBackbone backbone,
        PremiseInvalidationProposal proposal) {
        if (state.premiseInvalidationAudits().stream().anyMatch(audit -> audit.proposalId().equals(proposal.proposalId()))) {
            return PremiseInvalidationAudit.Reason.DUPLICATE_PROPOSAL;
        }
        if (state.version() != proposal.expectedVersion()) return PremiseInvalidationAudit.Reason.STALE_RUNTIME_VERSION;
        if (!state.scenarioPackageId().equals(proposal.scenarioPackageId())
                || !backbone.scenarioPackageId().equals(proposal.scenarioPackageId())) {
            return PremiseInvalidationAudit.Reason.STALE_BACKBONE_REVISION;
        }
        if (backbone.revision() != proposal.backboneRevision()) return PremiseInvalidationAudit.Reason.STALE_BACKBONE_REVISION;
        var target = backbone.stages().stream().filter(stage -> stage.stageId().equals(proposal.targetStageId())).findFirst();
        if (target.isEmpty()) return PremiseInvalidationAudit.Reason.TARGET_STAGE_NOT_FOUND;
        int currentOrder = backbone.stages().stream().filter(stage -> stage.stageId().equals(state.stageId()))
                .mapToInt(com.dndmaster.adventure.domain.scenario.StageBackboneEntry::order).findFirst().orElse(0);
        if (target.get().order() <= currentOrder || state.stageHistory().stream().anyMatch(history -> history.stageId().equals(proposal.targetStageId()))) {
            return PremiseInvalidationAudit.Reason.TARGET_STAGE_ALREADY_STARTED;
        }
        if (proposal.reason().isBlank()) return PremiseInvalidationAudit.Reason.INVALIDATION_REASON_MISSING;
        Set<UUID> acceptedFacts = state.acceptedStoryFacts().stream().map(PlayCreatedStoryFact::factId).collect(java.util.stream.Collectors.toSet());
        if (proposal.supportingFactIds().isEmpty()) return PremiseInvalidationAudit.Reason.NO_ACCEPTED_FACT;
        if (!acceptedFacts.containsAll(proposal.supportingFactIds())) return PremiseInvalidationAudit.Reason.UNKNOWN_SUPPORTING_FACT;
        return PremiseInvalidationAudit.Reason.ACCEPTED;
    }

    private static PremiseInvalidationAudit audit(PremiseInvalidationProposal proposal, boolean accepted,
            PremiseInvalidationAudit.Reason reason) {
        return new PremiseInvalidationAudit(proposal.proposalId(), proposal.targetStageId(), accepted, reason,
                proposal.expectedVersion(), proposal.backboneRevision(), proposal.supportingFactIds());
    }
}
