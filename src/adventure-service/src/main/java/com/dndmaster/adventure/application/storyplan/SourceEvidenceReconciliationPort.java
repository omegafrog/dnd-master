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
                                && source.locator().equals(value.locator())).findFirst();
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
            }

            @Override public List<String> reconcile(List<AdventureStoryPlanGenerationPort.SourceCitation> authoritative,
                    List<AdventureStoryPlanGenerationPort.SourceCitation> candidate, TacticalScenePlan scene) {
                var errors = new java.util.ArrayList<>(reconcile(authoritative, candidate));
                String source = authoritative.stream().map(AdventureStoryPlanGenerationPort.SourceCitation::quote)
                        .filter(java.util.Objects::nonNull).reduce("", (left, right) -> left + " " + right).toLowerCase();
                String claims = java.util.stream.Stream.concat(
                        scene.bosses().stream().map(value -> value.id()),
                        java.util.stream.Stream.concat(scene.environments().stream().map(value -> value.kind()),
                                scene.outcomes().stream().map(value -> value.condition())))
                        .reduce("", (left, right) -> left + " " + right).toLowerCase();
                java.util.Set<String> terms = java.util.Set.of("dragon", "goblin", "orc", "rat", "swarm", "treasure", "trap", "brewery", "cellar");
                for (String term : terms) {
                    if (claims.contains(term) && !source.contains(term)) {
                        String alternative = terms.stream().filter(source::contains).findFirst().orElse(null);
                        if (alternative != null && !alternative.equals(term)) errors.add("tactical inference contradicts authoritative source: " + term);
                    }
                }
                return List.copyOf(errors);
            }
        };
    }
}
