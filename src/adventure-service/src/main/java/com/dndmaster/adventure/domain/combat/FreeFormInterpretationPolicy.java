package com.dndmaster.adventure.domain.combat;

import java.util.UUID;

public final class FreeFormInterpretationPolicy {
    private FreeFormInterpretationPolicy() { }

    /** Spell use requires character spell-resource data that the free-form action path does not provide. */
    public static boolean requestsSpellUse(String text) {
        if (text == null || text.isBlank()) return false;
        String normalized = text.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("시전") || normalized.contains("주문")
                || normalized.contains("마법 화살") || normalized.contains("magic missile")
                || normalized.contains("cast spell") || normalized.contains("cast a spell");
    }

    /** Input validation does not consult or require the structured action catalog. */
    public static FreeFormActionDeclaration accept(UUID actorId, String text) {
        return new FreeFormActionDeclaration(actorId, text);
    }
}
