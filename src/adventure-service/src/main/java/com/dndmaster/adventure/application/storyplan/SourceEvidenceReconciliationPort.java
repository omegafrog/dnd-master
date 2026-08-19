package com.dndmaster.adventure.application.storyplan;

import java.util.List;

/** Reconciles AI citations with authoritative source evidence before a plan can be READY. */
public interface SourceEvidenceReconciliationPort {
    List<String> reconcile(List<AdventureStoryPlanGenerationPort.SourceCitation> authoritative,
            List<AdventureStoryPlanGenerationPort.SourceCitation> candidate);

    static SourceEvidenceReconciliationPort exact() {
        return (authoritative, candidate) -> {
            var errors = new java.util.ArrayList<String>();
            for (var value : candidate) {
                var match = authoritative.stream().filter(source ->
                        source.documentId().equals(value.documentId()) && source.locator().equals(value.locator())).findFirst();
                if (match.isEmpty()) {
                    errors.add("unknown tactical source citation");
                    continue;
                }
                var source = match.get();
                if (source.extractionVersion() != value.extractionVersion()
                        || !java.util.Objects.equals(source.quote(), value.quote())) {
                    errors.add("tactical source evidence does not match the authoritative extraction");
                }
            }
            return List.copyOf(errors);
        };
    }
}
