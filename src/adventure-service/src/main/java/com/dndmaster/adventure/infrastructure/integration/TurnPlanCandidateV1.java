package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.application.runtime.PlanCandidate;
import com.dndmaster.adventure.application.runtime.TurnPlan;
import java.util.List;
import java.util.Set;

/** Explicit AI GM v2 boundary DTO. Versioning stays at the adapter, not the domain model. */
public record TurnPlanCandidateV1(String candidateId, String scene, String npcState, String judgment,
                                  List<String> revealableFacts, List<String> forbiddenFacts,
                                  String playerIntent, String stateFingerprint, String situationKey,
                                  String informationBoundary, Set<String> referencedEntities,
                                  boolean preservesAgency, boolean continuitySafe, boolean ruleCompliant,
                                  int complexity) {
    public static TurnPlanCandidateV1 from(PlanCandidate candidate) {
        TurnPlan plan = candidate.plan();
        return new TurnPlanCandidateV1(candidate.candidateId(), plan.scene(), plan.npcState(), plan.judgment(),
                plan.revealableFacts(), plan.forbiddenFacts(), candidate.playerIntent(), candidate.stateFingerprint(),
                candidate.situationKey(), candidate.informationBoundary(), candidate.referencedEntities(),
                candidate.preservesAgency(), candidate.continuitySafe(), candidate.ruleCompliant(), candidate.complexity());
    }

    public PlanCandidate toDomain() {
        return new PlanCandidate(candidateId, new TurnPlan(scene, npcState, judgment, revealableFacts, forbiddenFacts),
                playerIntent, stateFingerprint, situationKey, informationBoundary, referencedEntities,
                preservesAgency, continuitySafe, ruleCompliant, complexity);
    }
}
