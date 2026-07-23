package com.dndmaster.adventure.application.scenario.compilation;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.ResolutionKind;
import com.dndmaster.adventure.domain.scenario.ResolutionVisibility;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import java.util.List;

public record ResolutionCandidate(
        ResolutionKind kind,
        String abilityOrSkill,
        Integer dc,
        String diceExpression,
        ResolutionVisibility visibility,
        String sourceQuote,
        List<ScenarioSourceReference> sourceRefs,
        String provenance) {
    public static ResolutionCandidate skillCheck(
            KnowledgeDocumentId documentId, long extractionVersion, String skill, Integer dc, String quote) {
        return new ResolutionCandidate(
                ResolutionKind.SKILL_ABILITY_CHECK,
                skill,
                dc,
                null,
                ResolutionVisibility.GM_REFERENCE,
                quote,
                List.of(new ScenarioSourceReference(documentId, extractionVersion, "quote:" + quote.hashCode())),
                "schema-v1");
    }

    public static ResolutionCandidate diceRoll(
            KnowledgeDocumentId documentId, long extractionVersion, String diceExpression, String quote) {
        return new ResolutionCandidate(
                ResolutionKind.DICE_ROLL,
                null,
                null,
                diceExpression,
                ResolutionVisibility.GM_REFERENCE,
                quote,
                List.of(new ScenarioSourceReference(documentId, extractionVersion, "quote:" + quote.hashCode())),
                "schema-v1");
    }
}
