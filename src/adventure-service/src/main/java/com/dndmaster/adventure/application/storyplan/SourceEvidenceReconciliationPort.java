package com.dndmaster.adventure.application.storyplan;

import com.dndmaster.adventure.domain.adventure.TacticalScenePlan;
import java.util.List;

/** Reconciles AI citations with authoritative source evidence before a plan can be READY. */
public interface SourceEvidenceReconciliationPort {
    List<String> reconcile(List<AdventureStoryPlanGenerationPort.SourceCitation> authoritative,
            List<AdventureStoryPlanGenerationPort.SourceCitation> candidate);

    default List<String> reconcile(List<AdventureStoryPlanGenerationPort.SourceCitation> authoritative,
            List<AdventureStoryPlanGenerationPort.SourceCitation> candidate, TacticalScenePlan scene) {
        return reconcile(authoritative, candidate);
    }

    static SourceEvidenceReconciliationPort exact() {
        return new SourceEvidenceReconciliationPort() {
            @Override public List<String> reconcile(List<AdventureStoryPlanGenerationPort.SourceCitation> authoritative,
                    List<AdventureStoryPlanGenerationPort.SourceCitation> candidate) {
            var errors = new java.util.ArrayList<String>();
            for (var value : candidate) {
                var match = authoritative.stream().filter(source ->
                        source.documentType().equals(value.documentType())
                                && source.documentId().equals(value.documentId())
                                && source.extractionVersion() == value.extractionVersion()
                                && source.locator().equals(value.locator())).findFirst();
                if (match.isEmpty()) {
                    errors.add("unknown tactical source citation");
                    continue;
                }
                var source = match.get();
                if (!java.util.Objects.equals(source.quote(), value.quote())) {
                    errors.add("tactical source evidence does not match the authoritative extraction");
                }
            }
            return List.copyOf(errors);
            }

        };
    }
}
