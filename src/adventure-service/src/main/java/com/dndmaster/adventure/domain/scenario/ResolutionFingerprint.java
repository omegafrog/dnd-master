package com.dndmaster.adventure.domain.scenario;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public final class ResolutionFingerprint {
    private ResolutionFingerprint() {}

    public static String candidateFingerprint(Object value) {
        return hash(value == null ? "null" : value.toString());
    }

    public static String overrideAnchorFingerprint(ResolutionCandidateSnapshot snapshot) {
        StringBuilder value = new StringBuilder();
        value.append(normalize(snapshot.documentFingerprint())).append('|')
                .append(normalize(snapshot.locatorFingerprint())).append('|')
                .append(normalize(snapshot.quoteFingerprint())).append('|')
                .append(normalize(snapshot.contextFingerprint())).append('|')
                .append(normalize(snapshot.unitFingerprint()));
        return hash(value.toString());
    }

    public static String candidateAnchorFingerprint(
            ResolutionKind kind,
            String abilityOrSkill,
            SaveDc dc,
            String diceExpression,
            ResolutionVisibility visibility,
            String sourceQuote,
            List<ScenarioSourceReference> sourceRefs,
            ScenarioResolutionDetail detail) {
        return overrideAnchorFingerprint(new ResolutionCandidateSnapshot(
                candidateDocumentFingerprint(sourceRefs),
                candidateLocatorFingerprint(sourceRefs),
                candidateQuoteFingerprint(sourceQuote),
                candidateContextFingerprint(detail),
                candidateUnitFingerprint(kind, abilityOrSkill, dc, diceExpression, visibility, detail)));
    }

    public static String candidateDocumentFingerprint(List<ScenarioSourceReference> sourceRefs) {
        return hash(normalize(sourceRefs == null ? null : sourceRefs.stream()
                .map(ref -> ref == null
                        ? "null"
                        : ref.knowledgeDocumentId().value().toString() + "|" + ref.locator())
                .sorted()
                .toList().toString()));
    }

    public static String candidateLocatorFingerprint(List<ScenarioSourceReference> sourceRefs) {
        return candidateDocumentFingerprint(sourceRefs);
    }

    public static String candidateQuoteFingerprint(String sourceQuote) {
        return hash(normalize(sourceQuote));
    }

    public static String candidateContextFingerprint(ScenarioResolutionDetail detail) {
        return hash(normalize(detail == null ? null : detail.triggerCondition()) + '|'
                + normalize(detail == null ? null : detail.actor()) + '|'
                + normalize(detail == null ? null : detail.roller()) + '|'
                + normalize(detail == null ? null : detail.instructionVisibility()) + '|'
                + normalize(detail == null ? null : detail.resultVisibility()) + '|'
                + normalize(detail == null ? null : detail.modifiers().toString()) + '|'
                + normalize(detail == null ? null : detail.advantageState()) + '|'
                + normalize(detail == null ? null : detail.reroll()));
    }

    public static String candidateUnitFingerprint(
            ResolutionKind kind,
            String abilityOrSkill,
            SaveDc dc,
            String diceExpression,
            ResolutionVisibility visibility,
            ScenarioResolutionDetail detail) {
        return hash(normalize(kind == null ? null : kind.name()) + '|'
                + normalize(abilityOrSkill) + '|'
                + normalize(dc == null ? null : dc.toString()) + '|'
                + normalize(diceExpression) + '|'
                + normalize(visibility == null ? null : visibility.name()) + '|'
                + normalize(stableDetailFingerprint(detail)));
    }

    private static String stableDetailFingerprint(ScenarioResolutionDetail detail) {
        if (detail == null) return "";
        StringBuilder value = new StringBuilder();
        value.append(normalize(detail.triggerCondition())).append('|')
                .append(normalize(detail.actor())).append('|')
                .append(normalize(detail.roller())).append('|')
                .append(normalize(detail.instructionVisibility())).append('|')
                .append(normalize(detail.resultVisibility())).append('|')
                .append(normalize(detail.modifiers().toString())).append('|')
                .append(normalize(detail.advantageState())).append('|')
                .append(normalize(detail.reroll())).append('|');
        for (ScenarioResolutionDetail.Step step : detail.steps()) {
            value.append(normalize(step.id())).append('|')
                    .append(normalize(step.kind() == null ? null : step.kind().name())).append('|')
                    .append(normalize(step.abilityOrSkill())).append('|')
                    .append(normalize(step.dc() == null ? null : step.dc().toString())).append('|')
                    .append(normalize(step.diceExpression())).append('|')
                    .append(normalize(step.condition())).append('|')
                    .append(normalize(step.nextStepIds().toString())).append('|')
                    .append(normalize(step.successOutcomeIds().toString())).append('|')
                    .append(normalize(step.failureOutcomeIds().toString())).append(';');
        }
        for (ScenarioResolutionDetail.Outcome outcome : detail.outcomes()) {
            value.append(normalize(outcome.id())).append('|')
                    .append(normalize(outcome.label())).append('|')
                    .append(normalize(outcome.description())).append(';');
        }
        for (ScenarioResolutionDetail.TableEntry entry : detail.randomTable()) {
            value.append(normalize(entry.range())).append('|')
                    .append(normalize(entry.outcome())).append(';');
        }
        value.append(normalize(detail.tableCoverage()));
        return value.toString();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public record ResolutionCandidateSnapshot(
            String documentFingerprint,
            String locatorFingerprint,
            String quoteFingerprint,
            String contextFingerprint,
            String unitFingerprint) {}
}
