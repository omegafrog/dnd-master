package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.combat.FreeFormActionDeclaration;
import com.dndmaster.adventure.domain.combat.FreeFormInterpretationPolicy;
import java.util.Objects;

/** Separate command identity for the free-form UI while retaining combat version semantics. */
public record FreeFormCombatCommand(CombatActionCommand action, FreeFormActionDeclaration declaration) {
    public FreeFormCombatCommand {
        Objects.requireNonNull(action, "combat action command must not be null");
        Objects.requireNonNull(declaration, "free-form declaration must not be null");
        if (!action.characterSheetId().value().equals(declaration.actorId())) {
            throw new IllegalArgumentException("free-form actor does not match command character");
        }
    }

    public FreeFormCombatCommand(CombatActionCommand action, String text) {
        this(action, FreeFormInterpretationPolicy.accept(action.characterSheetId().value(), text));
    }

    public String fingerprint() {
        return action.fingerprint() + "|FREE_FORM|" + declaration.text();
    }
}
