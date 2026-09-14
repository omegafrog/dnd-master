package com.dndmaster.adventure.application.combat;

/** The map is prepared, but no valid player start cell has been confirmed. */
public final class CombatMapPlacementRequiredException extends RuntimeException {
    public CombatMapPlacementRequiredException() {
        super("맵 시작 위치를 선택해야 모험을 시작할 수 있습니다.");
    }
}
