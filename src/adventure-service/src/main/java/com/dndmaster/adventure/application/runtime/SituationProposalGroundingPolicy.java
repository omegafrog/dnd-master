package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.RuntimeAddedFact;
import com.dndmaster.adventure.domain.scenario.ScenarioModel;
import java.util.List;
import java.util.UUID;

/** Validates the GM's source precedence before a proposed situation becomes persistent state. */
public final class SituationProposalGroundingPolicy {
    private SituationProposalGroundingPolicy() {}

    public static RuntimeResolutionProposal ground(RuntimeResolutionProposal proposal, ScenarioModel scenarioModel,
            List<RuntimeEvidence> rulesEvidence, UUID turnId) {
        if (proposal.situationProposal() == null) return proposal;
        SituationProposal situation = proposal.situationProposal();
        switch (situation.basis()) {
            case SCENARIO -> {
                if (!scenarioModel.containsElement(situation.reference())) {
                    throw new IllegalArgumentException("SITUATION_SCENARIO_REFERENCE_REQUIRED");
                }
            }
            case RAG -> {
                boolean found = rulesEvidence.stream().anyMatch(evidence -> situation.reference().equals(evidence.citationKey())
                        || situation.reference().equals(evidence.locator()));
                if (!found) throw new IllegalArgumentException("SITUATION_RAG_REFERENCE_REQUIRED");
            }
            case FALLBACK -> {
                String description = describe(situation.update());
                List<RuntimeAddedFact> facts = description.isBlank() ? proposal.runtimeAddedFacts()
                        : List.of(new RuntimeAddedFact(UUID.randomUUID(), description, turnId));
                return new RuntimeResolutionProposal(proposal.gameStateDelta(), proposal.disclosureState(),
                        situation.update(), facts, proposal.completionProposal(), situation);
            }
        }
        return new RuntimeResolutionProposal(proposal.gameStateDelta(), proposal.disclosureState(), situation.update(),
                proposal.runtimeAddedFacts(), proposal.completionProposal(), situation);
    }

    private static String describe(SituationUpdateProposal update) {
        return java.util.stream.Stream.of(
                value("location", update.location()), value("problem", update.problem()),
                value("threat", update.threat()), value("goal", update.goal()))
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.joining("; "));
    }

    private static String value(String field, String value) {
        return value == null ? null : field + "=" + value;
    }
}
