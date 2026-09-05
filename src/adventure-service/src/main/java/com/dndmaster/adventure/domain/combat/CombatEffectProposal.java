package com.dndmaster.adventure.domain.combat;

import java.util.List;
import java.util.Objects;

/** Machine-readable effects in an AI proposal; GM prose is never an effect. */
public record CombatEffectProposal(int hitPointDelta, int currencyDelta, List<String> addItems,
                                   List<String> removeItems, CombatMapEffect mapEffect) {
    public CombatEffectProposal {
        addItems = clean(addItems, "add items");
        removeItems = clean(removeItems, "remove items");
        if (addItems.stream().anyMatch(removeItems::contains)) {
            throw new IllegalArgumentException("an item cannot be added and removed in one proposal");
        }
    }

    public static CombatEffectProposal none() {
        return new CombatEffectProposal(0, 0, List.of(), List.of(), null);
    }

    public static CombatEffectProposal damage(int hitPointDelta) {
        return new CombatEffectProposal(hitPointDelta, 0, List.of(), List.of(), null);
    }

    public boolean hasCharacterEffects() {
        return hitPointDelta != 0 || currencyDelta != 0 || !addItems.isEmpty() || !removeItems.isEmpty();
    }

    public boolean hasEffects() {
        return hasCharacterEffects() || mapEffect != null;
    }

    private static List<String> clean(List<String> values, String name) {
        Objects.requireNonNull(values, name + " must not be null");
        return values.stream().map(value -> {
            if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not contain blank items");
            return value.trim();
        }).distinct().toList();
    }
}
