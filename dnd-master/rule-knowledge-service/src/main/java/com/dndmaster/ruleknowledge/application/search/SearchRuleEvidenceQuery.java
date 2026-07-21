package com.dndmaster.ruleknowledge.application.search;

import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.util.List;
import java.util.Objects;

public record SearchRuleEvidenceQuery(
        OwnerPlayerId owner,
        List<RulebookId> selectedRulebooks,
        String situation,
        int limit) {

    public SearchRuleEvidenceQuery {
        Objects.requireNonNull(owner, "owner must not be null");
        Objects.requireNonNull(selectedRulebooks, "selectedRulebooks must not be null");
        if (selectedRulebooks.isEmpty()) {
            throw new IllegalArgumentException("at least one selected rulebook is required");
        }
        Objects.requireNonNull(situation, "situation must not be null");
        if (situation.isBlank()) {
            throw new IllegalArgumentException("situation must not be blank");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        selectedRulebooks = List.copyOf(selectedRulebooks);
    }
}
