package com.dndmaster.combatmap.domain;

import java.util.Objects;

public record Door(GridPosition position, boolean open, boolean locked) {
    public Door(GridPosition position, boolean open) { this(position, open, false); }
    public Door {
        Objects.requireNonNull(position);
        if (open && locked) throw new IllegalArgumentException("열린 문은 잠긴 상태일 수 없습니다");
    }
    public Door opened() {
        if (locked) throw new CombatMapMovementDeniedException("잠긴 문은 열 수 없습니다");
        return new Door(position, true, false);
    }
}
