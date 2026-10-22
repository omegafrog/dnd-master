package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.CurrentSituation;

/** Validates Situation proposals and gives new identity only to material changes. */
public final class SituationUpdatePolicy {
    private SituationUpdatePolicy() {}

    public static CurrentSituation apply(CurrentSituation current, SituationUpdateProposal proposal) {
        return apply(current, proposal, null, null, null);
    }

    public static CurrentSituation apply(CurrentSituation current, SituationUpdateProposal proposal,
            String title, String description, java.util.UUID mapDefinitionId) {
        if (current == null || proposal == null) throw new NullPointerException("situation inputs must not be null");
        String location = valueOr(proposal.location(), current.location());
        String problem = valueOr(proposal.problem(), current.problem());
        String threat = valueOr(proposal.threat(), current.threat());
        String goal = valueOr(proposal.goal(), current.goal());
        boolean material = title != null || !current.location().equals(location) || !current.problem().equals(problem)
                || !current.threat().equals(threat) || !current.goal().equals(goal)
                || (title != null && !current.title().equals(title))
                || (description != null && !current.description().equals(description))
                || (mapDefinitionId != null && !mapDefinitionId.equals(current.mapDefinitionId()));
        if (proposal.kind() == SituationUpdateProposal.Kind.CONTINUE || !material) {
            return new CurrentSituation(current.situationId(), current.revision() + 1,
                    current.location(), problem, threat, goal,
                    current.activeCombatScenarioId(), current.firstNarration(), current.title(), current.description(), current.mapDefinitionId());
        }
        return new CurrentSituation(java.util.UUID.randomUUID(), 1,
                location, problem, threat, goal, null, null,
                title == null ? location : title, description == null ? problem : description, mapDefinitionId);
    }

    private static String valueOr(String value, String fallback) {
        return value == null ? fallback : value;
    }
}
