package com.dndmaster.ruleknowledge.application.pipeline;

import com.dndmaster.ruleknowledge.domain.rulebook.ProcessingStatus;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.util.List;
import java.util.Objects;

public record RulebookProcessingResult(
        RulebookId rulebookId,
        ProcessingStatus status,
        List<String> warnings) {

    public RulebookProcessingResult {
        Objects.requireNonNull(rulebookId, "rulebookId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
