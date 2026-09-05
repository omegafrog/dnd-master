package com.dndmaster.adventure.domain.combat;

import java.util.Objects;

/**
 * End boundary guard. It has no repository or provider dependencies so an end
 * proposal cannot bypass the pending-work safety checks.
 */
public final class CombatEndGuard {
    public Decision check(CombatEncounter encounter, CombatEndProposal proposal,
                          boolean pendingAction, boolean pendingWork, boolean externalEffectsCommitted) {
        if (encounter == null) return Decision.rejected("ENCOUNTER_REQUIRED");
        var proposalViolations = CombatEndProposalPolicy.validate(proposal, encounter.adventureId(), encounter.encounterId());
        if (!proposalViolations.isEmpty()) return Decision.rejected(proposalViolations.getFirst());
        if (encounter.status() == CombatEncounter.Status.ENDED) return Decision.rejected("COMBAT_ALREADY_ENDED");
        if (pendingAction) return Decision.rejected("PENDING_ACTION");
        if (encounter.pendingReaction() != null) return Decision.rejected("PENDING_REACTION");
        if (pendingWork) return Decision.rejected("PENDING_WORK");
        if (!externalEffectsCommitted) return Decision.rejected("EXTERNAL_EFFECTS_NOT_COMMITTED");
        return Decision.ok();
    }

    public void requireAllowed(CombatEncounter encounter, CombatEndProposal proposal,
                               boolean pendingAction, boolean pendingWork, boolean externalEffectsCommitted) {
        Decision decision = check(encounter, proposal, pendingAction, pendingWork, externalEffectsCommitted);
        if (!decision.accepted()) throw new CombatEndRejectedException(decision.code());
    }

    public record Decision(boolean accepted, String code) {
        public Decision {
            Objects.requireNonNull(code, "end guard code must not be null");
        }
        public static Decision ok() { return new Decision(true, "ACCEPTED"); }
        public static Decision rejected(String code) { return new Decision(false, code); }
    }
}
