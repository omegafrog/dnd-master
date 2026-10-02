package com.dndmaster.adventure.application.combat;

@FunctionalInterface
public interface CombatNarrationPort {
    String narrate(CombatNarrationRequest request);

    static CombatNarrationPort disabled() {
        return request -> null;
    }
}
