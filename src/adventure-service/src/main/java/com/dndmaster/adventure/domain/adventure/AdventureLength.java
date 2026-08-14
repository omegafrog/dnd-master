package com.dndmaster.adventure.domain.adventure;

public enum AdventureLength {
    SHORT(3, 4),
    STANDARD(4, 6),
    LONG(7, 8);

    private final int minimumStages;
    private final int maximumStages;

    AdventureLength(int minimumStages, int maximumStages) {
        this.minimumStages = minimumStages;
        this.maximumStages = maximumStages;
    }

    public int minimumStages() { return minimumStages; }
    public int maximumStages() { return maximumStages; }
}
