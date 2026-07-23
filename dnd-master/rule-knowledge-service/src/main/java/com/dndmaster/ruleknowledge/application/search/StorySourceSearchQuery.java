package com.dndmaster.ruleknowledge.application.search;

import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record StorySourceSearchQuery(
        OwnerPlayerId owner,
        List<StorySourceScope> packageScope,
        List<String> activeLocators,
        String situation,
        int limit) {
    public StorySourceSearchQuery {
        Objects.requireNonNull(owner, "owner must not be null");
        packageScope = List.copyOf(Objects.requireNonNull(packageScope, "package scope must not be null"));
        if (packageScope.isEmpty()) {
            throw new IllegalArgumentException("package scope must not be empty");
        }
        if (new HashSet<>(packageScope).size() != packageScope.size()) {
            throw new IllegalArgumentException("package scope must not contain duplicate document versions");
        }
        activeLocators = activeLocators == null ? List.of() : List.copyOf(activeLocators);
        if (situation == null || situation.isBlank()) {
            throw new IllegalArgumentException("situation must not be blank");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
    }
}
