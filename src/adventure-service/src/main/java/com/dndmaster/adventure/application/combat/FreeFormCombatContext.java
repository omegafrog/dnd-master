package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.combat.CombatEncounter;
import com.dndmaster.adventure.domain.combat.FreeFormActionDeclaration;
import java.util.Objects;

/** Player-safe context supplied to the AI decision boundary. */
public record FreeFormCombatContext(CombatEncounter encounter, FreeFormActionDeclaration declaration) {
    public FreeFormCombatContext {
        Objects.requireNonNull(declaration, "free-form declaration must not be null");
    }
}
