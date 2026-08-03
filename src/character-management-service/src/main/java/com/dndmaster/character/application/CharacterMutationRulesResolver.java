package com.dndmaster.character.application;

import com.dndmaster.character.domain.CharacterMutationRules;
import com.dndmaster.character.domain.SheetEdition;

@FunctionalInterface
public interface CharacterMutationRulesResolver {
    CharacterMutationRules rulesFor(SheetEdition edition);

    static CharacterMutationRulesResolver permissive() {
        return ignored -> CharacterMutationRules.allowAll();
    }
}
