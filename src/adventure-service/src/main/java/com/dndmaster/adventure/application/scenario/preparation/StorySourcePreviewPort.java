package com.dndmaster.adventure.application.scenario.preparation;

import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import com.dndmaster.adventure.domain.scenario.RequiredSituation;
import java.util.List;

/** Reads every located passage of the selected original story document. */
@FunctionalInterface
public interface StorySourcePreviewPort {
    List<Passage> load(ScenarioBundleDocumentSelection document, java.util.UUID ownerId, java.util.UUID bundleId);

    default List<List<String>> findApplicableRules(List<ScenarioBundleDocumentSelection> rulebooks,
            java.util.UUID ownerId, java.util.UUID bundleId, List<RequiredSituation> situations) {
        return situations.stream().map(ignored -> List.<String>of()).toList();
    }

    record Passage(ScenarioSourceReference sourceRef, String text) {
        public Passage {
            java.util.Objects.requireNonNull(sourceRef);
            if (text == null || text.isBlank()) throw new IllegalArgumentException("source passage text is required");
        }
    }
}
