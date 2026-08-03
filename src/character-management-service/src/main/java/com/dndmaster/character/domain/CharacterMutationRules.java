package com.dndmaster.character.domain;

import java.util.Objects;

@FunctionalInterface
public interface CharacterMutationRules {
    CharacterMutationDecision evaluate(CharacterSheetData current, CharacterSheetData proposed);

    static CharacterMutationRules allowAll() {
        return (current, proposed) -> {
            Objects.requireNonNull(current, "current character data must not be null");
            Objects.requireNonNull(proposed, "proposed character data must not be null");
            return CharacterMutationDecision.accept();
        };
    }
}
