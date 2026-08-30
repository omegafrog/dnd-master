package com.dndmaster.adventure.application.storyplan;

import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import com.dndmaster.adventure.domain.adventure.ClaimOrigin;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Deterministic contract checks; semantic compatibility remains outside this slice. */
public final class StoryPlanStructuralGuard {
    public List<String> validate(List<AdventureStoryPlanStage> stages) {
        List<String> violations = new ArrayList<>();
        Set<Integer> positions = new HashSet<>();
        for (AdventureStoryPlanStage stage : stages == null ? List.<AdventureStoryPlanStage>of() : stages) {
            if (!positions.add(stage.position())) violations.add("duplicate stage position: " + stage.position());
            if (stage.schemaVersion() != AdventureStoryPlanStage.CURRENT_SCHEMA_VERSION) violations.add("unsupported story plan schema version");
            Set<String> citationKeys = new HashSet<>();
            stage.evidence().forEach(evidence -> {
                if (evidence.citationKey() == null || evidence.citationKey().isBlank()) violations.add("evidence citation key is required");
                else if (!citationKeys.add(evidence.citationKey())) violations.add("duplicate evidence citation key: " + evidence.citationKey());
            });
            stage.sourceFactClaims().forEach(claim -> {
                if (claim.origin() == ClaimOrigin.SOURCE && claim.citationKeys().isEmpty()) violations.add("SOURCE claim requires citation keys: " + claim.fieldPath());
                claim.citationKeys().stream().filter(key -> !citationKeys.contains(key))
                        .forEach(key -> violations.add("unknown claim citation key: " + key));
            });
        }
        return List.copyOf(violations);
    }
}
