package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.CurrentSituation;

/** Validates Situation proposals and gives new identity only to material changes. */
public final class SituationUpdatePolicy {
    private SituationUpdatePolicy() {}

    public static CurrentSituation apply(CurrentSituation current, SituationUpdateProposal proposal) {
        if (current == null || proposal == null) throw new NullPointerException("situation inputs must not be null");
        boolean material = !current.location().equals(proposal.location())
                || !current.problem().equals(proposal.problem())
                || !current.threat().equals(proposal.threat())
                || !current.goal().equals(proposal.goal());
        if (proposal.kind() == SituationUpdateProposal.Kind.CONTINUE || !material) {
            return new CurrentSituation(current.situationId(), current.revision() + 1,
                    current.location(), proposal.problem(), proposal.threat(), proposal.goal());
        }
        return new CurrentSituation(java.util.UUID.randomUUID(), 1,
                proposal.location(), proposal.problem(), proposal.threat(), proposal.goal());
    }
}
