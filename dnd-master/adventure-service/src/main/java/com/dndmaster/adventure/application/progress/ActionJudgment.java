package com.dndmaster.adventure.application.progress;

import com.dndmaster.adventure.domain.adventure.RuleSetId;
import java.util.Objects;

public record ActionJudgment(RuleSetId ruleSetId, String result) {
    public ActionJudgment {
        Objects.requireNonNull(ruleSetId, "rule set id must not be null");
        if (result == null || result.isBlank()) throw new IllegalArgumentException("result must not be blank");
        result = result.trim();
    }
}
