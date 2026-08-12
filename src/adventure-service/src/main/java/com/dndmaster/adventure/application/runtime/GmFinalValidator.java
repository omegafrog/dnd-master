package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.AdventureContext;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
        if (!evidencePack.storybook().isEmpty() && result.plan().citedEvidence().isEmpty()) {
            throw new IllegalStateException("storybook evidence must be cited for every GM turn");
        }
        if (!Objects.equals(result.plan().scene(), currentContext.currentScene())
                && result.plan().citedEvidence().stream().noneMatch(evidencePack.storybook()::contains)) {
            throw new IllegalStateException("scene transition requires a storybook citation");
        }
        if (result.plan().proposedActiveSourceContext() != null && allowed.stream().noneMatch(evidence ->
                evidence.knowledgeDocumentId().equals(result.plan().proposedActiveSourceContext().knowledgeDocumentId())
                        && evidence.extractionVersion() == result.plan().proposedActiveSourceContext().extractionVersion()
                        && evidence.locator().equals(result.plan().proposedActiveSourceContext().locator()))) {
            throw new IllegalStateException("active source is outside selected evidence");
        }
        String judgment = result.plan().judgment().toLowerCase(java.util.Locale.ROOT);
        String narration = result.plan().narration().toLowerCase(java.util.Locale.ROOT);
        boolean ruleClaim = (judgment + " " + narration).contains("rule") || (judgment + " " + narration).contains("must")
                || (judgment + " " + narration).contains("roll") || (judgment + " " + narration).contains("damage")
                || (judgment + " " + narration).contains("check");
        if (ruleClaim && result.plan().citedEvidence().isEmpty()) {
            throw new IllegalStateException("rule claim requires citation");
        }
        if (hiddenData.stream().filter(Objects::nonNull).anyMatch(secret ->
                !secret.isBlank() && result.plan().narration().contains(secret))) {
            throw new IllegalStateException("GM narration contains hidden data");
        }
        return result;
    }
}
