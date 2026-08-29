package com.dndmaster.adventure.application.runtime;

import java.util.ArrayList;
import java.util.List;

public final class DeterministicNarrativeValidator {
    public List<VerificationViolation> validate(NarrativeVerificationContext context, String draft) {
        if (draft == null || draft.isBlank()) throw new IllegalArgumentException("draft must not be blank");
        List<VerificationViolation> violations = new ArrayList<>();
        add(violations, context.hiddenFacts(), draft, VerificationViolationType.SECRET_LEAK, "remove secret information");
        add(violations, context.unsupportedFacts(), draft, VerificationViolationType.UNSUPPORTED_FACT, "remove unsupported fact");
        add(violations, context.ruleMismatches(), draft, VerificationViolationType.RULE_MISMATCH, "follow the resolved rules");
        add(violations, context.agencyViolations(), draft, VerificationViolationType.PLAYER_AGENCY_VIOLATION, "leave the player's choice open");
        add(violations, context.npcKnowledgeViolations(), draft, VerificationViolationType.NPC_KNOWLEDGE_VIOLATION, "respect NPC knowledge boundaries");
        add(violations, context.turnPlanDeviations(), draft, VerificationViolationType.TURNPLAN_DEVIATION, "preserve the resolved turn plan");
        add(violations, context.stateContradictions(), draft, VerificationViolationType.STATE_CONTRADICTION, "preserve committed state");
        return List.copyOf(violations);
    }

    private static void add(List<VerificationViolation> target, List<String> forbidden, String draft,
                            VerificationViolationType type, String instruction) {
        String lowerDraft = draft.toLowerCase(java.util.Locale.ROOT);
        for (String evidence : forbidden) {
            if (!evidence.isBlank() && lowerDraft.contains(evidence.toLowerCase(java.util.Locale.ROOT))) {
                target.add(new VerificationViolation(type, VerificationSeverity.ERROR, evidence, "draft", instruction));
            }
        }
    }
}
