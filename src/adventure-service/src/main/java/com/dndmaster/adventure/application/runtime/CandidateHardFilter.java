package com.dndmaster.adventure.application.runtime;

import java.util.ArrayList;
import java.util.List;

/** Deterministic admission gate. Judge ports only receive the admitted side. */
public final class CandidateHardFilter {
    public FilterResult filter(List<PlanCandidate> candidates, PlanningContext context) {
        List<PlanCandidate> valid = new ArrayList<>();
        List<Rejection> rejected = new ArrayList<>();
        for (PlanCandidate candidate : candidates) {
            List<Violation> violations = new ArrayList<>();
            if (!candidate.playerIntent().equals(context.playerIntent())) violations.add(Violation.PLAYER_INTENT_MISMATCH);
            if (!candidate.stateFingerprint().equals(context.stateFingerprint())) violations.add(Violation.STATE_MISMATCH);
            if (!candidate.situationKey().equals(context.situationKey())) violations.add(Violation.SITUATION_MISMATCH);
            if (!candidate.informationBoundary().equals(context.informationBoundary())) violations.add(Violation.INFORMATION_BOUNDARY_MISMATCH);
            if (!context.forbiddenFacts().stream().noneMatch(candidate.plan().revealableFacts()::contains)) violations.add(Violation.SECRET_LEAK);
            if (!context.supportedEntities().containsAll(candidate.referencedEntities())) violations.add(Violation.UNSUPPORTED_ENTITY);
            if (!candidate.preservesAgency()) violations.add(Violation.PLAYER_AGENCY_VIOLATION);
            if (!candidate.continuitySafe()) violations.add(Violation.CONTINUITY_VIOLATION);
            if (!candidate.ruleCompliant()) violations.add(Violation.RULE_VIOLATION);
            if (violations.isEmpty()) valid.add(candidate); else rejected.add(new Rejection(candidate, violations));
        }
        return new FilterResult(List.copyOf(valid), List.copyOf(rejected));
    }
    public record FilterResult(List<PlanCandidate> valid, List<Rejection> rejected) {}
    public record Rejection(PlanCandidate candidate, List<Violation> violations) {}
    public enum Violation { PLAYER_INTENT_MISMATCH, STATE_MISMATCH, SITUATION_MISMATCH,
        INFORMATION_BOUNDARY_MISMATCH, SECRET_LEAK, UNSUPPORTED_ENTITY,
        PLAYER_AGENCY_VIOLATION, CONTINUITY_VIOLATION, RULE_VIOLATION }
}
