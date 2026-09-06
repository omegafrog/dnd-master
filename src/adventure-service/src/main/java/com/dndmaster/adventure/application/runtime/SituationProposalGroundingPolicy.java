package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.RuntimeAddedFact;
import com.dndmaster.adventure.domain.scenario.ScenarioModel;
import java.util.List;
import java.util.UUID;

/** Validates the GM's source precedence before a proposed situation becomes persistent state. */
public final class SituationProposalGroundingPolicy {
    private SituationProposalGroundingPolicy() {}

    public static RuntimeResolutionProposal ground(RuntimeResolutionProposal proposal, ScenarioModel scenarioModel,
            List<RuntimeEvidence> storybookEvidence, UUID turnId) {
        if (proposal.situationProposal() == null) return proposal;
        SituationProposal situation = proposal.situationProposal();
        switch (situation.basis()) {
            case SCENARIO -> {
                if (!scenarioModel.containsElement(situation.reference())) {
                    throw new IllegalArgumentException("SITUATION_SCENARIO_REFERENCE_REQUIRED");
                }
            }
            case RAG -> {
                boolean found = storybookEvidence.stream().anyMatch(evidence -> situation.reference().equals(evidence.citationKey())
                        || situation.reference().equals(evidence.locator()));
                if (!found) throw new IllegalArgumentException("SITUATION_RAG_REFERENCE_REQUIRED");
            }
            case FALLBACK -> {
                RuntimeAddedFact fact = new RuntimeAddedFact(UUID.randomUUID(), describe(situation.update()), turnId);
                return new RuntimeResolutionProposal(proposal.gameStateDelta(), proposal.disclosureState(),
                        situation.update(), List.of(fact), proposal.completionProposal(), situation);
            }
        }
        return new RuntimeResolutionProposal(proposal.gameStateDelta(), proposal.disclosureState(), situation.update(),
                proposal.runtimeAddedFacts(), proposal.completionProposal(), situation);
    }

    private static String describe(SituationUpdateProposal update) {
        return "location=" + update.location() + "; problem=" + update.problem()
                + "; threat=" + update.threat() + "; goal=" + update.goal();
    }
}
