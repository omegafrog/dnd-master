package com.dndmaster.character.application;

import com.dndmaster.character.domain.CharacterMutationRules;
import com.dndmaster.character.domain.SheetEdition;

@FunctionalInterface
public interface CharacterMutationRulesResolver {
    CharacterMutationRules rulesFor(SheetEdition edition);

    static CharacterMutationRulesResolver standard() {
        CharacterMutationRules dnd5e2014 = new Dnd5e2014CharacterMutationRules();
        return edition -> switch (edition) {
            case DND_5E_2014 -> dnd5e2014;
            case DND_5E_2024 -> CharacterMutationRules.allowAll();
        };
    }

    static CharacterMutationRulesResolver permissive() {
        return ignored -> CharacterMutationRules.allowAll();
    }
}
