package com.dndmaster.adventure.domain.adventure;

import java.util.Objects;

public record AdventurePlanConfiguration(int endingCount, AdventureLength adventureLength) {
    public AdventurePlanConfiguration {
        if (endingCount < 1 || endingCount > 4) {
            throw new IllegalArgumentException("ending count must be between 1 and 4");
        }
        adventureLength = Objects.requireNonNull(adventureLength, "adventure length must not be null");
    }

    public static AdventurePlanConfiguration defaults() {
        return new AdventurePlanConfiguration(2, AdventureLength.STANDARD);
    }
}
