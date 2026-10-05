package com.dndmaster.character.application;

import java.util.List;
import java.util.Objects;

/** Runtime-only effects produced by an adjudicated combat result. */
public record RuntimeCharacterMutation(int hitPointDelta, int currencyDelta, List<String> addItems,
        List<String> removeItems, int consumeSpellSlotLevel) {
    public RuntimeCharacterMutation(int hitPointDelta, int currencyDelta, List<String> addItems, List<String> removeItems) {
        this(hitPointDelta, currencyDelta, addItems, removeItems, 0);
    }
    public RuntimeCharacterMutation {
        addItems = clean(addItems, "addItems");
        removeItems = clean(removeItems, "removeItems");
        if (addItems.stream().anyMatch(removeItems::contains)) throw new IllegalArgumentException("item cannot be added and removed together");
        if (consumeSpellSlotLevel < 0) throw new IllegalArgumentException("spell slot level cannot be negative");
    }
    public boolean hasEffects() { return hitPointDelta != 0 || currencyDelta != 0 || !addItems.isEmpty() || !removeItems.isEmpty() || consumeSpellSlotLevel > 0; }
    private static List<String> clean(List<String> values, String field) {
        Objects.requireNonNull(values, field + " must not be null");
        return values.stream().map(value -> { if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not contain blank items"); return value.trim(); }).distinct().toList();
    }
}
