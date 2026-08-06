package com.dndmaster.combatmap.domain;

import java.util.Objects;

public record Door(GridPosition position, boolean open) {
    public Door { Objects.requireNonNull(position); }
    public Door opened() { return new Door(position, true); }
}
