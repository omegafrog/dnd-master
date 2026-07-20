package com.dndmaster.adventure.domain.ruleset;

import java.util.Objects;

public record RuleApplicationRequest(DndEdition edition, RulebookId rulebookId) {
    public RuleApplicationRequest {
        Objects.requireNonNull(edition, "edition must not be null");
        Objects.requireNonNull(rulebookId, "rulebook id must not be null");
    }
}
