package com.dndmaster.adventure.domain.combat;

import java.util.List;
import java.util.Map;

public record CombatSpellcastingProfile(List<CombatSpell> availableSpells, Map<Integer, Integer> availableSlots) {
    public CombatSpellcastingProfile {
        availableSpells = List.copyOf(availableSpells == null ? List.of() : availableSpells);
        availableSlots = Map.copyOf(availableSlots == null ? Map.of() : availableSlots);
        if (availableSlots.entrySet().stream().anyMatch(entry -> entry.getKey() < 1 || entry.getValue() < 0)) {
            throw new IllegalArgumentException("spell slot values are invalid");
        }
    }

    public static CombatSpellcastingProfile empty() {
        return new CombatSpellcastingProfile(List.of(), Map.of());
    }
}
