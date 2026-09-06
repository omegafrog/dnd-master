package com.dndmaster.adventure.domain.runtime.story;

import com.dndmaster.adventure.domain.scenario.StageBackbone;
import com.dndmaster.adventure.domain.scenario.StageBackboneEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Builds an immutable new backbone while preserving the completed/prefix stages. */
public final class FutureSuffixReplanningPolicy {
    private FutureSuffixReplanningPolicy() {}

    public static StageBackbone replaceFrom(StageBackbone current, String targetStageId,
            List<StageBackboneEntry> replacementSuffix) {
        Objects.requireNonNull(current, "current backbone must not be null");
        if (targetStageId == null || targetStageId.isBlank()) throw new IllegalArgumentException("target stage id is required");
        List<StageBackboneEntry> suffix = List.copyOf(Objects.requireNonNull(replacementSuffix, "replacement suffix must not be null"));
        if (suffix.isEmpty()) throw new IllegalArgumentException("replacement suffix must not be empty");
        int targetOrder = current.stages().stream().filter(stage -> stage.stageId().equals(targetStageId.trim()))
                .mapToInt(StageBackboneEntry::order).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("target stage is not in backbone"));
        List<StageBackboneEntry> result = new ArrayList<>();
        current.stages().stream().filter(stage -> stage.order() < targetOrder).forEach(result::add);
        for (int index = 0; index < suffix.size(); index++) {
            StageBackboneEntry entry = suffix.get(index);
            result.add(new StageBackboneEntry(entry.stageId(), targetOrder + index, entry.role(), entry.coreProblem(),
                    entry.funnelSummary(), entry.sourceRefs()));
        }
        return new StageBackbone(current.scenarioPackageId(), current.revision() + 1, result, current.sourceRefs());
    }
}
