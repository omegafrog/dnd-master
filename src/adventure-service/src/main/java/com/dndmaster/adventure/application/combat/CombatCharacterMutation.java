package com.dndmaster.adventure.application.combat;

import java.util.List;
import java.util.Objects;

/** Explicit, machine-readable character effects; never derived from GM prose. */
public record CombatCharacterMutation(int hitPointDelta, int currencyDelta, List<String> addItems,
        List<String> removeItems) {
    public CombatCharacterMutation {
        addItems = cleanItems(addItems, "add items");
        removeItems = cleanItems(removeItems, "remove items");
        if (addItems.stream().anyMatch(removeItems::contains)) {
            throw new IllegalArgumentException("an item cannot be added and removed in one outcome");
        }
    }

    public static CombatCharacterMutation none() {
        return new CombatCharacterMutation(0, 0, List.of(), List.of());
    }

    public boolean hasEffects() {
        return hitPointDelta != 0 || currencyDelta != 0 || !addItems.isEmpty() || !removeItems.isEmpty();
    }

    private static List<String> cleanItems(List<String> values, String name) {
        Objects.requireNonNull(values, name + " must not be null");
        return values.stream().map(value -> {
            if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not contain blank items");
            return value.trim();
        }).distinct().toList();
    }
}
