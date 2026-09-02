package com.dndmaster.adventure.application.runtime;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Immutable proof that a resolved player turn changed at least one meaningful dimension. */
public record MeaningfulProgress(Set<MeaningfulProgressCategory> categories) {
    public MeaningfulProgress {
        Objects.requireNonNull(categories, "progress categories must not be null");
        if (categories.isEmpty()) throw new IllegalArgumentException("progress categories must not be empty");
        categories = Set.copyOf(EnumSet.copyOf(categories));
    }

    public static MeaningfulProgress of(MeaningfulProgressCategory category) {
        return new MeaningfulProgress(Set.of(Objects.requireNonNull(category, "progress category must not be null")));
    }

    public static MeaningfulProgress of(Set<MeaningfulProgressCategory> categories) {
        return new MeaningfulProgress(categories);
    }

    public boolean contains(MeaningfulProgressCategory category) {
        return categories.contains(category);
    }
}
