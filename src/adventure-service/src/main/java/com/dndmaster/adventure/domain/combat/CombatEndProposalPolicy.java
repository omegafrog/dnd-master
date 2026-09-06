package com.dndmaster.adventure.domain.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Deterministic validation for AI GM end proposals. */
public final class CombatEndProposalPolicy {
    private CombatEndProposalPolicy() {}

    public static List<String> validate(CombatEndProposal proposal, UUID adventureId, UUID encounterId) {
        List<String> violations = new ArrayList<>();
        if (proposal == null) return List.of("END_PROPOSAL_REQUIRED");
        if (!proposal.accepted()) violations.add("END_PROPOSAL_NOT_ACCEPTED");
        boolean systemDefeat = proposal.source() == CombatEndProposal.Source.SYSTEM
                && proposal.reason() == CombatEndProposal.Reason.ENEMIES_DEFEATED;
        if (proposal.source() != CombatEndProposal.Source.GM && !systemDefeat) violations.add("GM_PROPOSAL_REQUIRED");
        if (!java.util.Objects.equals(proposal.adventureId(), adventureId)) violations.add("ADVENTURE_MISMATCH");
        if (!java.util.Objects.equals(proposal.encounterId(), encounterId)) violations.add("ENCOUNTER_MISMATCH");
        if (proposal.summary().isBlank()) violations.add("END_SUMMARY_REQUIRED");
        return List.copyOf(violations);
    }

    public static void requireValid(CombatEndProposal proposal, UUID adventureId, UUID encounterId) {
        List<String> violations = validate(proposal, adventureId, encounterId);
        if (!violations.isEmpty()) throw new IllegalArgumentException(String.join(",", violations));
    }
}
