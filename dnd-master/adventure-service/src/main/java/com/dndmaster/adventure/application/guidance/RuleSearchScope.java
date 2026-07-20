package com.dndmaster.adventure.application.guidance;

import com.dndmaster.adventure.domain.inquiry.RulebookId;
import java.util.List;
import java.util.Objects;

public record RuleSearchScope(boolean ready, List<RulebookId> selectedRulebooks) {
    public RuleSearchScope {
        selectedRulebooks = List.copyOf(Objects.requireNonNull(selectedRulebooks, "selected rulebooks must not be null"));
        if (ready && selectedRulebooks.isEmpty()) throw new IllegalArgumentException("a ready scope needs selected rulebooks");
    }
}
