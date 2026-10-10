package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.CurrentSituation;

/** Validates Situation proposals and gives new identity only to material changes. */
public final class SituationUpdatePolicy {
    private SituationUpdatePolicy() {}

    public static CurrentSituation apply(CurrentSituation current, SituationUpdateProposal proposal) {
        if (current == null || proposal == null) throw new NullPointerException("situation inputs must not be null");
        String location = valueOr(proposal.location(), current.location());
        String problem = valueOr(proposal.problem(), current.problem());
        String threat = valueOr(proposal.threat(), current.threat());
        String goal = valueOr(proposal.goal(), current.goal());
        boolean material = !current.location().equals(location) || !current.problem().equals(problem)
                || !current.threat().equals(threat) || !current.goal().equals(goal);
        if (proposal.kind() == SituationUpdateProposal.Kind.CONTINUE || !material) {
            return new CurrentSituation(current.situationId(), current.revision() + 1,
                    current.location(), problem, threat, goal,
                    current.activeCombatScenarioId(), current.firstNarration());
        }
        return new CurrentSituation(java.util.UUID.randomUUID(), 1,
                location, problem, threat, goal);
    }

    private static String valueOr(String value, String fallback) {
        return value == null ? fallback : value;
    }
}
