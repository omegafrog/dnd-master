package com.dndmaster.adventure.domain.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

/** Orders exploration groups by their saved initiative and randomizes exact ties once. */
public final class ExplorationInitiativePolicy {
    private ExplorationInitiativePolicy() { }

    public static List<UUID> order(Map<UUID, Integer> initiativeByGroup, Random random) {
        Objects.requireNonNull(initiativeByGroup, "group initiatives must not be null");
        Objects.requireNonNull(random, "random source must not be null");
        if (initiativeByGroup.isEmpty() || initiativeByGroup.keySet().stream().anyMatch(Objects::isNull)
                || initiativeByGroup.values().stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("group initiatives must be non-empty and complete");
        }
        var orderedScores = initiativeByGroup.values().stream().distinct()
                .sorted(Comparator.reverseOrder()).toList();
        List<UUID> result = new ArrayList<>(initiativeByGroup.size());
        for (Integer score : orderedScores) {
            List<UUID> tied = new ArrayList<>(initiativeByGroup.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(score))
                    .map(Map.Entry::getKey).toList());
            java.util.Collections.shuffle(tied, random);
            result.addAll(tied);
        }
        return List.copyOf(result);
    }
}
