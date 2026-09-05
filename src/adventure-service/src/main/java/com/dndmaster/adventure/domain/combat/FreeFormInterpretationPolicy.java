package com.dndmaster.adventure.domain.combat;

import java.util.UUID;

public final class FreeFormInterpretationPolicy {
    private FreeFormInterpretationPolicy() { }

    /** Input validation does not consult or require the structured action catalog. */
    public static FreeFormActionDeclaration accept(UUID actorId, String text) {
        return new FreeFormActionDeclaration(actorId, text);
    }
}
