package com.dndmaster.adventure.application.scenario.compilation;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.ResolutionKind;
import com.dndmaster.adventure.domain.scenario.ScenarioResolutionDetail;
import com.dndmaster.adventure.domain.scenario.ResolutionVisibility;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import com.dndmaster.adventure.domain.scenario.SaveDc;
import java.util.List;

public record ResolutionCandidate(
        ResolutionKind kind,
        String abilityOrSkill,
        SaveDc dc,
        String diceExpression,
        ResolutionVisibility visibility,
        String sourceQuote,
        List<ScenarioSourceReference> sourceRefs,
        String provenance,
        ScenarioResolutionDetail detail) implements com.dndmaster.adventure.domain.scenario.ResolutionCandidate {
    public ResolutionCandidate(ResolutionKind kind, String abilityOrSkill, int dc, String diceExpression,
            ResolutionVisibility visibility, String sourceQuote, List<ScenarioSourceReference> sourceRefs,
            String provenance, ScenarioResolutionDetail detail) {
        this(kind, abilityOrSkill, SaveDc.fixed(dc), diceExpression, visibility, sourceQuote, sourceRefs, provenance, detail);
    }
    public static ResolutionCandidate skillCheck(
            KnowledgeDocumentId documentId, long extractionVersion, String locator,
            String skill, Integer dc, String quote) {
        return new ResolutionCandidate(
                ResolutionKind.SKILL_ABILITY_CHECK,
                skill,
                SaveDc.fixed(dc),
                null,
                ResolutionVisibility.GM_REFERENCE,
                quote,
                List.of(new ScenarioSourceReference(documentId, extractionVersion, locator)),
                "schema-v1",
                null);
    }

    public static ResolutionCandidate diceRoll(
            KnowledgeDocumentId documentId, long extractionVersion, String locator,
            String diceExpression, String quote) {
        return new ResolutionCandidate(
                ResolutionKind.DICE_ROLL,
                null,
                (SaveDc) null,
                diceExpression,
                ResolutionVisibility.GM_REFERENCE,
                quote,
                List.of(new ScenarioSourceReference(documentId, extractionVersion, locator)),
                "schema-v1",
                null);
    }
}
