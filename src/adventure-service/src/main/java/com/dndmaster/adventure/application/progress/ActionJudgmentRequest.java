package com.dndmaster.adventure.application.progress;

import com.dndmaster.adventure.domain.adventure.RuleSetId;
import java.util.Objects;

public record ActionJudgmentRequest(RuleSetId ruleSetId, SceneProgress sceneProgress, String action) {
    public ActionJudgmentRequest {
        Objects.requireNonNull(ruleSetId, "rule set id must not be null");
        Objects.requireNonNull(sceneProgress, "scene progress must not be null");
        if (action == null || action.isBlank()) throw new IllegalArgumentException("action must not be blank");
        action = action.trim();
    }
}
