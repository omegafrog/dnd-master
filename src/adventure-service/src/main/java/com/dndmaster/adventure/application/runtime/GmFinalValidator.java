package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.AdventureContext;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Locale;

/** Fail-closed boundary between provider output and player-visible narration. */
public final class GmFinalValidator {
    public GmPlanResult validate(
            GmPlanResult result, EvidencePack evidencePack, AdventureContext currentContext, Set<String> hiddenData) {
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(evidencePack, "evidence pack must not be null");
        Objects.requireNonNull(currentContext, "current context must not be null");
        hiddenData = Set.copyOf(Objects.requireNonNull(hiddenData, "hidden data must not be null"));
        if (!result.stateDelta().isEmpty()) throw new IllegalStateException("read-only GM result contains state delta");

        List<RuntimeEvidence> allowed = List.of(
                evidencePack.storybook(), evidencePack.rulebook(), evidencePack.resolution())
                .stream().flatMap(List::stream).toList();
        if (result.plan().citedEvidence().stream().anyMatch(citation -> !allowed.contains(citation))) {
            throw new IllegalStateException("GM citation is outside selected evidence");
        }
        if (result.plan().proposedActiveSourceContext() != null && allowed.stream().noneMatch(evidence ->
                evidence.knowledgeDocumentId().equals(result.plan().proposedActiveSourceContext().knowledgeDocumentId())
                        && evidence.extractionVersion() == result.plan().proposedActiveSourceContext().extractionVersion()
                        && evidence.locator().equals(result.plan().proposedActiveSourceContext().locator()))) {
            throw new IllegalStateException("active source is outside selected evidence");
        }
        String judgment = result.plan().judgment().toLowerCase(Locale.ROOT);
        String narration = result.plan().narration().toLowerCase(Locale.ROOT);
        String output = judgment + " " + narration;
        boolean ruleClaim = containsAny(output, "rule", "must", "roll", "check", "saving throw", "proficiency");
        if (ruleClaim && result.plan().citedEvidence().stream()
                .noneMatch(evidence -> evidence.evidenceType() == RuntimeEvidenceType.RULEBOOK)) {
            throw new IllegalStateException("rule claim requires rulebook evidence");
        }
        boolean outcomeClaim = containsAny(output, "hits", "misses", "succeeds", "fails", "takes damage",
                "damage", "natural 20", "critical hit", "total");
        if (containsAny(output, "unresolved", "awaiting roll", "roll pending", "pending roll")) {
            throw new IllegalStateException("unresolved roll cannot be finalized");
        }
        if (outcomeClaim && result.plan().citedEvidence().stream()
                .noneMatch(evidence -> evidence.evidenceType() == RuntimeEvidenceType.RESOLUTION)) {
            throw new IllegalStateException("outcome requires supplied resolution evidence");
        }
        if (outcomeClaim && result.plan().citedEvidence().stream()
                .filter(evidence -> evidence.evidenceType() == RuntimeEvidenceType.RESOLUTION)
                .anyMatch(evidence -> evidence.context().stream().anyMatch(marker ->
                        marker.equalsIgnoreCase("resolution-status=PARTIAL")
                                || marker.equalsIgnoreCase("resolution-status=INVALID")
                                || marker.toLowerCase(Locale.ROOT).contains("conflict")))) {
            throw new IllegalStateException("resolution is incomplete or conflicting");
        }
        for (RuntimeEvidence evidence : result.plan().citedEvidence()) {
            if (evidence.evidenceType() == RuntimeEvidenceType.STORYBOOK
                    && evidence.visibility() != StoryEvidenceVisibility.PLAYER_VISIBLE
                    && narration.contains(evidence.excerpt().toLowerCase(Locale.ROOT))) {
                if (evidence.visibility() != StoryEvidenceVisibility.REVEALED_AFTER_EVENT
                        || evidence.disclosureEvent() == null
                        || !output.contains(evidence.disclosureEvent().toLowerCase(Locale.ROOT))) {
                    throw new IllegalStateException("undisclosed story evidence in player narration");
                }
            }
        }
        if (hiddenData.stream().filter(Objects::nonNull).map(secret -> secret.toLowerCase(Locale.ROOT)).anyMatch(secret ->
                !secret.isBlank() && narration.contains(secret))) {
            throw new IllegalStateException("GM narration contains hidden data");
        }
        return result;
    }

    private static boolean containsAny(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }
}
